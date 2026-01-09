import copy as cp
import os
import csv
import json
import re
import tempfile
import datetime
import shutil

from typing import Dict, List, Any, Union
from pathlib import Path

# -------------------------- 全局临时文件/重命名任务管理 --------------------------
# 存储所有临时文件路径映射到原文件，用于批量替换/清理
TEMP_FILES: Dict[str, str] = {}
# 存储重命名任务，批量执行
RENAME_TASKS: List[tuple] = []
# 存储需要清理的_EN/_CN文件路径（统一批量清理）
TO_CLEAN_SUFFIX_FILES: List[tuple] = []

# 全局当前语言设置（由 data/config/settings.json 的 aEP_UseEnString 决定）
USE_EN_SETTING_OLD: Union[bool, None] = None
USE_EN_SETTING_NEW: Union[bool, None] = None

# 备份目录（延迟初始化）
_BACKUP_DIR: Union[str, None] = None

# -------------------------- 临时文件操作函数 --------------------------
def create_temp_file(content: str, original_file_path: str) -> str:
    """
    创建临时文件（与原文件同目录），返回临时文件路径
    :param content: 要写入临时文件的内容
    :param original_file_path: 原文件路径（用于确定临时文件位置）
    :return: 临时文件绝对路径
    """
    original_path = Path(original_file_path)
    # 生成临时文件（后缀.tmp，与原文件同目录）
    temp_fd, temp_path = tempfile.mkstemp(
        suffix='.tmp',
        prefix=original_path.stem + '_',
        dir=str(original_path.parent)
    )
    # 写入内容并关闭文件句柄（UTF-8编码，保留中文）
    with os.fdopen(temp_fd, 'w', encoding='utf-8') as f:
        f.write(content)
    # 记录临时文件路径 -> 原文件路径 映射（用于原子替换时恢复原名/扩展名）
    TEMP_FILES[temp_path] = str(original_path)
    return temp_path

def write_to_temp_csv(rows: List[Dict[str, str]], original_file_path: str) -> str:
    """
    将CSV数据写入临时文件（保留中文逗号，仅处理半角逗号转义）
    :param rows: CSV行数据
    :param original_file_path: 原文件路径
    :return: 临时文件路径
    """
    if not rows:
        raise ValueError("无数据可写入临时CSV文件")
    # 构建CSV内容
    fieldnames = list(rows[0].keys())
    csv_content_lines: List[str] = []
    # 表头（半角逗号分隔）
    csv_content_lines.append(','.join(fieldnames))

    for row in rows:
        escaped_row = []
        # ensure we iterate in header order so columns align
        for k in fieldnames:
            v = row.get(k, '')
            val_str = '' if v is None else str(v)
            # 仅处理CSV标准转义：含半角逗号/双引号/换行符的字段需用双引号包裹
            if (',' in val_str) or ('"' in val_str) or ('\n' in val_str):
                val_str = val_str.replace('"', '""')  # 双引号转义为两个
                val_str = f'"{val_str}"'  # 包裹双引号
            # 中文逗号（，）不做任何处理，保留为字符串内容
            escaped_row.append(val_str)
        csv_content_lines.append(','.join(escaped_row))  # 半角逗号分隔字段

    csv_content = '\n'.join(csv_content_lines)
    # 写入临时文件
    return create_temp_file(csv_content, original_file_path)

def write_to_temp_json(data: Union[Dict[str, Any], List[Any]], original_file_path: str) -> str:
    """
    将JSON数据写入临时文件（保留中文逗号，UTF-8编码）
    :param data: JSON字典/列表数据
    :param original_file_path: 原文件路径
    :return: 临时文件路径
    """
    # 增强空数据校验，添加日志提示具体文件
    if data is None:
        raise ValueError(f"无数据可写入临时JSON文件（文件路径：{original_file_path}）")
    # 格式化JSON内容（ensure_ascii=False保留中文，indent=2保持缩进）
    json_content = json.dumps(data, ensure_ascii=False, indent=2)
    # 写入临时文件
    return create_temp_file(json_content, original_file_path)

def batch_replace_original_files() -> None:
    """批量将临时文件替换为原文件（原子操作，跨平台兼容）"""
    # iterate over a snapshot because we'll modify TEMP_FILES inside loop
    for temp_path, original_path in list(TEMP_FILES.items()):
        try:
            if os.path.exists(temp_path) and os.path.exists(original_path):
                # 使用os.replace保证原子替换（跨平台，避免文件损坏）
                os.replace(temp_path, original_path)
                print(f"✅ 原子替换完成：{original_path} ← {temp_path}")
            elif os.path.exists(temp_path) and not os.path.exists(original_path):
                # 原文件不存在，直接重命名临时文件为原文件
                os.rename(temp_path, original_path)
                print(f"✅ 重命名临时文件为原文件：{temp_path} → {original_path}")
            else:
                # temp file missing or both missing
                print(f"⚠️ 临时文件或原文件不存在：{temp_path} / {original_path}")
        finally:
            # 无论成功与否，移除映射以避免重复处理
            TEMP_FILES.pop(temp_path, None)

def clean_temp_files() -> None:
    """清理所有临时文件，避免残留"""
    for temp_path in list(TEMP_FILES.keys()):
        if os.path.exists(temp_path):
            try:
                os.remove(temp_path)
                print(f"🗑️ 清理临时文件：{temp_path}")
            except Exception as e:
                print(f"⚠️ 清理临时文件失败 {temp_path}：{e}")
        # 移除映射
        TEMP_FILES.pop(temp_path, None)
    # 清空临时文件映射表
    TEMP_FILES.clear()

# -------------------------- 通用工具函数 --------------------------
def clean_json_content(raw_content: str) -> str:
    """
    清理JSON内容（保留中文逗号，仅处理语法级问题）：
    - 移除不可见控制字符
    - 统一换行
    - 移除 BOM
    - 移除行内/行尾的 # 注释（但保留字符串内的 #）
    - 移除末尾多余的半角逗号
    """
    # 步骤1：移除不可见控制字符（避免干扰解析，不碰中文逗号）
    control_chars = [
        '\u200b', '\u200c', '\u200d',  # 零宽空格/连接符
        '\x00', '\x01', '\x02', '\x03', '\x04', '\x05',  # 空字符/控制字符
        '\v', '\f', '\x1c', '\x1d', '\x1e', '\x1f'  # 垂直制表符/换页符
    ]
    for char in control_chars:
        raw_content = raw_content.replace(char, '')

    # 步骤2：统一换行符为\n
    raw_content = raw_content.replace('\r\n', '\n').replace('\r', '\n')

    # 步骤3：移除UTF-8 BOM头
    if raw_content.startswith('\ufeff'):
        raw_content = raw_content.lstrip('\ufeff')

    # 步骤4：按字符遍历移除 # 注释，但保留字符串内的内容
    out_chars: List[str] = []
    in_string = False
    string_quote = ''
    escape = False
    i = 0
    length = len(raw_content)
    while i < length:
        ch = raw_content[i]
        if escape:
            # previous was backslash, so this char is escaped inside string
            out_chars.append(ch)
            escape = False
            i += 1
            continue
        if in_string:
            if ch == '\\':
                # start escape sequence
                out_chars.append(ch)
                escape = True
                i += 1
                continue
            out_chars.append(ch)
            if ch == string_quote:
                in_string = False
                string_quote = ''
            i += 1
            continue
        # not in string
        if ch == '"' or ch == "'":
            in_string = True
            string_quote = ch
            out_chars.append(ch)
            i += 1
            continue
        if ch == '#':
            # skip until newline (remove the comment). keep the newline if present
            # advance i to next newline or EOF
            while i < length and raw_content[i] != '\n':
                i += 1
            # if newline exists, append it to preserve line breaks
            if i < length and raw_content[i] == '\n':
                out_chars.append('\n')
                i += 1
            continue
        # normal char outside string
        out_chars.append(ch)
        i += 1

    clean_content = ''.join(out_chars)

    # 步骤5：移除末尾多余半角逗号（在 ] 或 } 之前的逗号）
    # 使用正则去除逗号后面只跟空白再跟 ] 或 }
    clean_content = re.sub(r',\s*(?=[\]}])', '', clean_content)

    # 最后去除多余空行并右侧空白
    lines = [ln.rstrip() for ln in clean_content.split('\n') if ln.strip() != '']
    return '\n'.join(lines)

def read_json_safely(file_path: str) -> Union[Dict[str, Any], List[Any]]:
    """
    安全读取JSON文件：
    1. 保留字符串内的中文逗号（，）
    2. 自动处理UTF-8 BOM头
    3. 提示语法位置的中文逗号错误
    4. 新增：空数据检测并提示
    """
    try:
        # 用utf-8-sig编码读取，自动识别并移除BOM头
        with open(file_path, 'r', encoding='utf-8-sig') as f:
            raw_content = f.read()
        raw_content = raw_content.strip()

        # 检测空文件
        if not raw_content:
            raise ValueError(f"JSON文件内容为空：{file_path}")

        # 清理注释/多余半角逗号，保留中文逗号
        clean_content = clean_json_content(raw_content)

        # 再次检测清理后的数据是否为空
        if not clean_content:
            raise ValueError(f"JSON文件清理后无有效内容：{file_path}")

        # 解析JSON
        data = json.loads(clean_content)

        # 检测解析后的数据是否为空
        if data is None or data == {} or data == []:
            raise ValueError(f"JSON文件解析后为空（空字典/列表）：{file_path}")

        print(f"✅ 成功读取JSON文件（有效数据）：{file_path}")
        return data

    except FileNotFoundError:
        raise
    except json.JSONDecodeError as e:
        error_msg = f"\n❌ JSON解析错误（文件：{file_path}）：{e}\n"
        error_msg += "⚠️ 可能原因：\n"
        error_msg += "  1. 中文逗号（，）出现在JSON语法位置（如分隔符），请改为半角逗号（,）；\n"
        error_msg += "  2. 字符串内的中文逗号无需修改（如\"desc\": \"测试，内容\"是合法的）。\n"
        print(error_msg)
        # 打印错误位置附近内容，便于定位问题
        error_range = clean_content[max(0, e.pos-10):e.pos+10] if 'clean_content' in locals() else "无有效内容"
        print(f"📌 错误位置附近内容：{repr(error_range)}")
        raise
    except Exception as e:
        print(f"\n❌ 读取JSON文件失败（文件：{file_path}）：{e}")
        raise

def get_abs_file_path(relative_path: str) -> str:
    """获取脚本所在目录的绝对路径，解决相对路径问题"""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    return os.path.join(script_dir, relative_path)

def get_current_aep_setting() -> Union[bool, None]:
    """读取 data/config/settings.json 中的 aEP_UseEnString（只读）。返回 True/False 或 None（无法读取）"""
    try:
        settings_path = get_abs_file_path('data/config/settings.json')
        settings = read_json_safely(settings_path)
        if isinstance(settings, dict) and 'aEP_UseEnString' in settings:
            return bool(settings['aEP_UseEnString'])
        return None
    except Exception:
        return None

def get_backup_dir() -> str:
    """返回本次运行的备份目录路径（在项目下的 swapped_backups/<timestamp>/），并确保目录存在"""
    global _BACKUP_DIR
    if _BACKUP_DIR:
        return _BACKUP_DIR
    repo_root = os.path.dirname(os.path.abspath(__file__))
    ts = datetime.datetime.now().strftime('%Y%m%d_%H%M%S')
    _BACKUP_DIR = os.path.join(repo_root, 'swapped_backups', ts)
    os.makedirs(_BACKUP_DIR, exist_ok=True)
    return _BACKUP_DIR

# ========== 通用后缀文件清理函数（仅收集待清理文件，不立即执行） ==========
def collect_clean_task(base_dir: str, base_name: str, ext: str, use_en_setting: bool) -> None:
    """
    收集需要清理的_EN/_CN文件任务（不立即清理，统一批量执行）
    :param base_dir: 文件所在目录
    :param base_name: 文件基础名（不含后缀和_EN/_CN，如 "submarkets"）
    :param ext: 文件扩展名（带点，如 .csv、.json、.faction）
    :param use_en_setting: 全局的USE_EN_SETTING_NEW值（True=使用英文，保留_CN；False=使用中文，保留_EN）
    """
    # 构建EN/CN文件完整路径
    en_file = os.path.join(base_dir, f"{base_name}_EN{ext}")
    cn_file = os.path.join(base_dir, f"{base_name}_CN{ext}")

    # 统一清理规则：True保留_CN删_EN，False保留_EN删_CN
    delete_file = en_file if use_en_setting else cn_file
    
    # 收集待清理文件（去重）
    if delete_file not in TO_CLEAN_SUFFIX_FILES and os.path.exists(delete_file):
        TO_CLEAN_SUFFIX_FILES.append(delete_file)

def batch_clean_extra_suffix_files() -> None:
    """
    批量执行_EN/_CN文件清理（所有替换/重命名完成后统一执行）
    """
    if not TO_CLEAN_SUFFIX_FILES:
        print(f"ℹ️ 无需要清理的_EN/_CN后缀文件")
        return
    
    print(f"\n=== 开始批量清理多余的_EN/_CN后缀文件 ===")
    for delete_file in TO_CLEAN_SUFFIX_FILES:
        try:
            os.remove(delete_file)
            print(f"🗑️ 已清理多余后缀文件：{delete_file}")
        except Exception as e:
            print(f"⚠️ 清理多余后缀文件失败 {delete_file}：{e}")
    
    # 清空待清理列表
    TO_CLEAN_SUFFIX_FILES.clear()

# -------------------------- CSV文件交换逻辑 --------------------------
def swap_file_csv(file_path: str, file_name_without_extension: str, swap_fields: list) -> None:
    """
    处理CSV文件交换（仅写入临时文件，不立即替换原文件）：
    1. 保留字段内的中文逗号
    2. 仅交换指定字段的值
    3. 收集清理任务（不立即清理）
    """
    # 初始化数据存储
    dict_rows_now: List[Dict[str, str]] = []
    dict_rows_other: List[Dict[str, str]] = []

    # 处理路径
    abs_file_path = get_abs_file_path(file_path)
    script_dir = os.path.dirname(abs_file_path)
    file_ext = os.path.splitext(abs_file_path)[1]  # 获取带点的文件后缀（如 .csv）

    # 构建EN/CN文件路径
    en_file_name = f"{file_name_without_extension}_EN{file_ext}"
    cn_file_name = f"{file_name_without_extension}_CN{file_ext}"
    abs_path_en = os.path.join(script_dir, en_file_name)
    abs_path_cn = os.path.join(script_dir, cn_file_name)

    # 验证输入路径
    if file_name_without_extension in abs_file_path:
        abs_path_en = abs_file_path.replace(file_name_without_extension, f"{file_name_without_extension}_EN")
        abs_path_cn = abs_file_path.replace(file_name_without_extension, f"{file_name_without_extension}_CN")
    assert abs_file_path != abs_path_en, f"文件路径/名称输入错误：{abs_file_path} vs {abs_path_en}"

    # 决定交换方向：优先使用全局设置 USE_EN_SETTING（True 表示当前为 EN）
    use_en = USE_EN_SETTING_NEW
    if use_en is None:
        raise Exception(f"aEP_UseEnString设置读取失败")

    # 读取主文件（主文件始终尝试读取为基础数据）
    try:
        with open(abs_file_path, 'r', encoding='utf-8') as f:
            csv_reader = csv.DictReader(f)
            for row in csv_reader:
                row_id = row.get('id', '').strip()
                if not row_id or row_id.startswith('#'):
                    dict_rows_now.append(row)  # 保留注释行，不参与交换
                    continue
                dict_rows_now.append(row)
        print(f"📄 成功加载主文件：{abs_file_path}")
    except FileNotFoundError as e:
        raise Exception(f"主CSV文件不存在或无法读取：{abs_file_path}：{e}")
    except Exception as e:
        raise Exception(f"主CSV文件读取异常：{e}")

    # which path to read, is new language is EN, read _EN
    preferred = abs_path_en if USE_EN_SETTING_NEW else abs_path_cn

    # load file
    if preferred:
        try:
            with open(preferred, 'r', encoding='utf-8') as f:
                csv_reader = csv.DictReader(f)
                for row in csv_reader:
                    row_id = row.get('id', '').strip()
                    if not row_id or row_id.startswith('#'):
                        dict_rows_other.append(row)
                        continue
                    dict_rows_other.append(row)
            print(f"📄 成功加载语言文件：{preferred}")
        except FileNotFoundError as e:
            raise Exception(f"语言文件未找到：{preferred}")
        except Exception as e:
            raise Exception(f"语言文件读取失败：{e}：{preferred}")

    # 检测空数据
    if not dict_rows_now:
        raise ValueError(f"主CSV文件读取后无有效数据：{abs_file_path}")
    if not dict_rows_other:
        raise ValueError(f"备用CSV文件（EN/CN）读取后无有效数据：{preferred if preferred else abs_path_en if os.path.exists(abs_path_en) else abs_path_cn}")

    # 提取有效ID（排除注释/空ID）
    valid_ids_now = {
        row['id'].strip() for row in dict_rows_now
        if row.get('id', '').strip() and not row['id'].strip().startswith('#')
    }
    valid_ids_other = {
        row['id'].strip() for row in dict_rows_other
        if row.get('id', '').strip() and not row['id'].strip().startswith('#')
    }
    common_ids = valid_ids_now.intersection(valid_ids_other)
    print(f"🔍 找到可交换的公共ID数量：{len(common_ids)}")

    # 交换指定字段的值
    for common_id in common_ids:
        # 找到对应ID的行
        row_now = next((r for r in dict_rows_now if r['id'].strip() == common_id), None)
        row_other = next((r for r in dict_rows_other if r['id'].strip() == common_id), None)
        if not row_now or not row_other:
            continue

        # 逐字段交换
        for field in swap_fields:
            try:
                # 跳过不存在的字段
                if field not in row_now or field not in row_other:
                    print(f"⚠️ 字段{field}在ID{common_id}中不存在，跳过")
                    continue

                # 保留原始值（含中文逗号），仅交换值
                val_now = row_now[field]
                val_other = row_other[field]

                # 处理字段内的JSON格式值（保留中文逗号）
                if val_now and val_other and val_now.startswith("{") and val_now.endswith("}") and val_other.startswith("{") and val_other.endswith("}"):
                    val_now = json.loads(clean_json_content(val_now))
                    val_other = json.loads(clean_json_content(val_other))

                # 交换值（保留所有字符，包括中文逗号）
                row_now[field] = str(val_other) if isinstance(val_other, (dict, list)) else val_other
                row_other[field] = str(val_now) if isinstance(val_now, (dict, list)) else val_now

            except Exception as e:
                raise Exception(f"ID{common_id}字段{field}交换失败：{e}")

    # 写入主文件到临时文件
    try:
        temp_main_path = write_to_temp_csv(dict_rows_now, abs_file_path)
        print(f"📝 主文件临时文件生成：{temp_main_path}")
    except Exception as e:
        raise Exception(f"主文件临时文件写入失败：{e}")

    # 写入备份文件到临时文件
    try:
        # target_path: after swapping, write the swapped-out (original main) to the opposite suffix
        # We want the main file to be the "current" language (USE_EN_SETTING_NEW == true indicates EN).
        # Therefore the backup (swapped-out file) must be the other language suffix.      
        target_path = abs_path_cn if USE_EN_SETTING_NEW else abs_path_en
        temp_backup_path = write_to_temp_csv(dict_rows_other, target_path)
        print(f"📝 备份文件临时文件生成：{temp_backup_path}")
    except Exception as e:
        raise Exception(f"备份文件临时文件写入失败：{e}")

    # ========== 修改：仅收集清理任务，不立即执行 ==========
    collect_clean_task(script_dir, file_name_without_extension, file_ext, USE_EN_SETTING_NEW)

# -------------------------- JSON文件交换逻辑 --------------------------
def swap_json(file_path: str, file_name_without_extension: str, extension: str = None) -> None:
    """
    处理JSON/faction文件交换（仅写入临时文件，不立即替换原文件）：
    1. 保留字符串内的中文逗号
    2. 递归交换JSON内的对应值
    3. 收集清理任务（不立即清理）
    """
    # 处理路径
    abs_file_path = get_abs_file_path(file_path)
    script_dir = os.path.dirname(abs_file_path)
    # 处理扩展名：如果传入extension（如faction）则用它，否则从路径提取（不带点）
    file_ext_raw = extension if extension else os.path.splitext(abs_file_path)[1].lstrip('.')
    file_ext = f".{file_ext_raw}"  # 转为带点的扩展名（如 .json、.faction）

    # 构建EN/CN文件路径
    en_file_name = f"{file_name_without_extension}_EN.{file_ext_raw}"
    cn_file_name = f"{file_name_without_extension}_CN.{file_ext_raw}"
    abs_path_en = os.path.join(script_dir, en_file_name)
    abs_path_cn = os.path.join(script_dir, cn_file_name)

    # 修正路径替换逻辑
    if file_name_without_extension in abs_file_path:
        abs_path_en = abs_file_path.replace(file_name_without_extension, f"{file_name_without_extension}_EN")
        abs_path_cn = abs_file_path.replace(file_name_without_extension, f"{file_name_without_extension}_CN")

    # 决定交换方向：优先使用全局设置 USE_EN_SETTING
    use_en = USE_EN_SETTING_NEW
    if use_en is None:
        raise Exception(f"aEP_UseEnString设置读取失败")

    data1 = None  # 主文件数据
    data2 = None  # 语言文件数据
    preferred = abs_path_en if USE_EN_SETTING_NEW else abs_path_cn

    # 读取主文件
    try:
        data1 = read_json_safely(abs_file_path)
        print(f"📄 成功加载主文件：{abs_file_path}")
    except FileNotFoundError as e:
        raise Exception(f"主文件加载失败：{e}")
    except Exception as e:
        raise Exception(f"JSON读取异常：{e}")

    # 读取备用（优先使用偏好）
    if preferred:
        try:
            data2 = read_json_safely(preferred)
            print(f"📄 成功加载语言文件：{preferred}")
        except FileNotFoundError as e:
            raise Exception(f"语言文件未找到：{preferred}")
        except Exception as e:
            raise Exception(f"语言文件读取失败：{e}：{preferred}")

    # 递归交换逻辑保持不变
    def swap_nested_json_values(data1: Union[Dict, List], data2: Union[Dict, List]):
        if isinstance(data1, dict) and isinstance(data2, dict):
            common_keys = set(data1.keys()).intersection(data2.keys())
            for key in common_keys:
                if isinstance(data1[key], (dict, list)) and isinstance(data2[key], (dict, list)):
                    swap_nested_json_values(data1[key], data2[key])
                else:
                    temp = cp.deepcopy(data2[key])
                    data2[key] = cp.deepcopy(data1[key])
                    data1[key] = temp
        elif isinstance(data1, list) and isinstance(data2, list):
            min_len = min(len(data1), len(data2))
            for i in range(min_len):
                if isinstance(data1[i], (dict, list)) and isinstance(data2[i], (dict, list)):
                    swap_nested_json_values(data1[i], data2[i])
                else:
                    temp = cp.deepcopy(data2[i])
                    data2[i] = cp.deepcopy(data1[i])
                    data1[i] = temp

    swap_nested_json_values(data1, data2)

    # 写入主文件到临时文件
    try:
        temp_main_path = write_to_temp_json(data1, abs_file_path)
        print(f"📝 主文件临时文件生成：{temp_main_path}")
    except Exception as e:
        raise Exception(f"主文件临时文件写入失败：{e}")

    # 写入备份（写到与偏好相反的后缀）
    try:
        target_path = abs_path_cn if USE_EN_SETTING_NEW else abs_path_en
        temp_backup_path = write_to_temp_json(data2, target_path)
        print(f"📝 备份文件临时文件生成：{temp_backup_path}")
    except Exception as e:
        raise Exception(f"备份文件临时文件写入失败：{e}")

    # ========== 修改：仅收集清理任务，不立即执行 ==========
    collect_clean_task(script_dir, file_name_without_extension, file_ext, USE_EN_SETTING_NEW)

# -------------------------- 文件重命名逻辑 --------------------------
def swap_name(file_path: str, file_name_with_ext: str) -> None:
    """预收集重命名任务，不立即执行重命名"""
    abs_file_path = get_abs_file_path(file_path)
    file_dir = os.path.dirname(abs_file_path)
    base_name, ext = os.path.splitext(file_name_with_ext)

    # 构建CN/EN文件名
    cn_file = f"{base_name}_CN{ext}"
    en_file = f"{base_name}_EN{ext}"
    cn_path = os.path.join(file_dir, cn_file)
    en_path = os.path.join(file_dir, en_file)

    # Always decide based on global USE_EN_SETTING (fallback to reading settings if None)
    use_en = USE_EN_SETTING_NEW
    if use_en is None:
        raise Exception(f"aEP_UseEnString设置读取失败")

    # When USE_EN_SETTING_NEW is True, current language should be main(CN) + main_EN -> final files: main (EN) + main_CN
    # So if an EN file exists, move EN -> original and original -> _CN
    if USE_EN_SETTING_NEW:
        if os.path.exists(en_path):
            RENAME_TASKS.append((abs_file_path, cn_path, en_path))
            print(f"📌 预收集重命名任务：{abs_file_path} -> {cn_path})")
        else:
            raise Exception(f"未找到后缀为XXX_EN的对应文件文件：{abs_file_path}")
    else:
        # USE_EN_SETTING_NEW is False, current language should be main(EN) + main_CN -> final files: main (CN) + main_EN
        # So if a CN file exists, move CN -> original and original -> _EN
        if os.path.exists(cn_path):
            RENAME_TASKS.append((abs_file_path, en_path, cn_path))
            print(f"📌 预收集重命名任务：：{abs_file_path} -> {en_path})")
        else:
            raise Exception(f"未找到后缀为XXX_CN的对应文件文件：{abs_file_path}")

def batch_execute_rename() -> None:
    """批量执行重命名任务（简化版，移除内部清理逻辑）
    核心逻辑：原文件 ↔ 目标后缀文件
    """
    for original_path, temp_path, swap_path in RENAME_TASKS:
        try:
            print(f"🔁 处理重命名任务: {original_path}")

            # ========== 核心重命名逻辑 ==========
            # 1. 原文件 → 临时后缀文件（如 original.csv → original_CN.csv）
            if os.path.exists(original_path):
                os.replace(original_path, temp_path)
                print(f"➡️ 原文件已移动到临时位置：{original_path} -> {temp_path}")
            else:
                print(f"⚠️ 原文件不存在，跳过移动：{original_path}")

            # 2. 目标交换文件 → 原文件位置（如 original_EN.csv → original.csv）
            if os.path.exists(swap_path):
                os.replace(swap_path, original_path)
                print(f"⬅️ 交换文件已替换到原位置：{swap_path} -> {original_path}")
            else:
                print(f"⚠️ 交换文件不存在，跳过替换：{swap_path}")

            # ========== 移除：原有的清理逻辑，改为统一收集任务 ==========
            file_dir = os.path.dirname(original_path)
            base_name = os.path.splitext(os.path.basename(original_path))[0]
            ext = os.path.splitext(original_path)[1]
            collect_clean_task(file_dir, base_name, ext, USE_EN_SETTING_NEW)

        except Exception as e:
            print(f"⚠️ 重命名失败 {original_path}：{e}")
            raise  # 抛出异常终止执行

    # 清空重命名任务列表
    RENAME_TASKS.clear()

# -------------------------- JSON配置更新逻辑 --------------------------
def update_setting_in_json(file_path: str, key: str, new_value: Any = None) -> Any:
    """更新JSON配置（保留中文逗号，写入临时文件）

    如果 new_value 为 None，则读取当前值并对布尔值取反后写回（返回新的值）。
    返回写入后的值，供调用者使用。
    """
    abs_file_path = get_abs_file_path(file_path)
    try:
        # 直接读取原始内容并清理，确保我们解析到实际的字典结构
        with open(abs_file_path, 'r', encoding='utf-8-sig') as f:
            raw = f.read()
        if raw is None or raw.strip() == '':
            raise ValueError(f"配置文件内容为空：{abs_file_path}")
        clean = clean_json_content(raw)
        data = json.loads(clean)

        if not isinstance(data, dict):
            raise ValueError(f"配置文件解析后不是字典：{abs_file_path}")

        # 仅修改指定键（保留其它键）
        if new_value is None:
            if key not in data:
                raise KeyError(f"键{key}不存在于配置文件中（可用键：{list(data.keys())}）")
            if not isinstance(data[key], bool):
                raise ValueError(f"键{key}不是布尔值，无法取反（当前值：{data[key]}，类型：{type(data[key])}）")
            data[key] = not data[key]
        else:
            data[key] = new_value

        print(f"🔧 更新配置：{key} = {data[key]}")

        # 将修改后的完整字典写入临时文件（不会丢失其它键）
        json_content = json.dumps(data, ensure_ascii=False, indent=2)
        temp_config_path = create_temp_file(json_content, abs_file_path)
        print(f"📝 配置文件临时文件生成：{temp_config_path}")

        return data[key]
    except FileNotFoundError:
        raise Exception(f"配置文件不存在：{abs_file_path}")
    except json.JSONDecodeError as e:
        raise Exception(f"配置文件JSON解析失败：{abs_file_path}：{e}")
    except Exception as e:
        raise Exception(f"配置更新异常：{e}")

# -------------------------- 主执行逻辑（原子性批量处理） --------------------------
if __name__ == "__main__":
    try:
        # 初始化全局语言设置（从配置文件读取）
        USE_EN_SETTING_OLD = get_current_aep_setting()
        USE_EN_SETTING_NEW = not USE_EN_SETTING_OLD

        # ========== 第一步：批量处理所有文件，生成临时文件/收集重命名任务 ==========
        print("=== 开始处理所有文件，生成临时文件 ===")

        # CSV文件交换（生成临时文件 + 收集清理任务）
        swap_file_csv("data/campaign/submarkets.csv", "submarkets", ['name', 'desc'])
        swap_file_csv("data/campaign/rules.csv", "rules", ['script','text','options'])
        swap_file_csv("data/campaign/industries.csv", "industries", ['name','desc'])
        swap_file_csv("data/campaign/special_items.csv", "special_items", ['name','tech/manufacturer','desc'])
        swap_file_csv("data/campaign/commodities.csv", "commodities", ['name'])
        swap_file_csv("data/campaign/market_conditions.csv", "market_conditions", ['name','desc'])
        swap_file_csv("data/strings/descriptions.csv", "descriptions", ['text1','text2','text3','text4','text5'])
        swap_file_csv("data/characters/skills/skill_data.csv", "skill_data", ['name','description','author'])
        swap_file_csv("data/shipsystems/ship_systems.csv", "ship_systems", ['name'])
        swap_file_csv("data/hulls/ship_data.csv", "ship_data", ['name','tech/manufacturer','designation'])
        swap_file_csv("data/hullmods/hull_mods.csv","hull_mods",['name','tech/manufacturer','uiTags','desc','short','sModDesc'])
        swap_file_csv("data/weapons/weapon_data.csv","weapon_data",['name','tech/manufacturer','primaryRoleStr','customPrimary','customAncillary'])
        swap_file_csv("data/config/LunaSettings.csv", "LunaSettings", ['fieldName','fieldDescription' ])

        # 文件重命名（预收集任务）
        swap_name("data/missions/aEP_eliminate_mission/descriptor.json", "descriptor.json")
        swap_name("data/missions/aEP_eliminate_mission/mission_text.txt", "mission_text.txt")
        swap_name("data/missions/aEP_first_contact/descriptor.json", "descriptor.json")
        swap_name("data/missions/aEP_first_contact/mission_text.txt", "mission_text.txt")
        swap_name("data/missions/aEP_planet_investigation/descriptor.json", "descriptor.json")
        swap_name("data/missions/aEP_planet_investigation/mission_text.txt", "mission_text.txt")
        swap_name("data/missions/aEP_assassination/descriptor.json", "descriptor.json")
        swap_name("data/missions/aEP_assassination/mission_text.txt", "mission_text.txt")
        swap_name("data/missions/aEP_test/descriptor.json", "descriptor.json")
        swap_name("data/missions/aEP_test/mission_text.txt", "mission_text.txt")
        swap_name("data/missions/aEP_random_fleet_combat/descriptor.json", "descriptor.json")
        swap_name("data/missions/aEP_random_fleet_combat/mission_text.txt", "mission_text.txt")

        # JSON/faction文件交换（生成临时文件 + 收集清理任务）
        swap_json("mod_info.json","mod_info")
        swap_json("data/config/modFiles/magicBounty_data.json", "magicBounty_data")
        swap_json("data/config/planets.json", "planets")
        swap_json("data/config/custom_entities.json", "custom_entities")
        swap_json("data/world/factions/aEP_FSF.faction", "aEP_FSF","faction")
        swap_json("data/world/factions/aEP_FSF_adv.faction", "aEP_FSF_adv","faction")

        # 配置更新（生成临时文件）
        # update_setting_in_json("data/config/settings.json", 'aEP_UseEnString', None)

        # ========== 第二步：所有文件处理完成，批量执行替换/重命名 ==========
        print("\n=== 所有临时文件生成完成，开始批量替换原文件 ===")
        # 批量替换临时文件为原文件
        batch_replace_original_files()
        # 批量执行重命名任务
        batch_execute_rename()

        # 最后更新设置（在重命名后执行，以便基于最新文件后缀状态）
        update_setting_in_json("data/config/settings.json", 'aEP_UseEnString', None)
        # 将设置临时文件应用到磁盘
        batch_replace_original_files()

        # ========== 第三步：所有替换/重命名完成后，统一清理_EN/_CN文件 ==========
        batch_clean_extra_suffix_files()

        print("\n🎉 所有文件交换/重命名/清理完成！")

    except Exception as e:
        # 任意步骤失败，清理所有临时文件，终止操作
        print(f"\n❌ 处理失败：{e}")
        print("🧹 清理临时文件...")
        clean_temp_files()
        exit(1)

    # 最后清理空的临时文件列表（冗余保护）
    clean_temp_files()