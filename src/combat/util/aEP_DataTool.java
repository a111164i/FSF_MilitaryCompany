//by a111164
package combat.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import org.json.JSONArray;
import org.json.JSONObject;
import org.lazywizard.lazylib.MathUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class aEP_DataTool
{
  CombatEngineAPI engine;

  public static String txt(String id) {
    if(Global.getSettings().getBoolean("aEP_UseEnString")) {
      return Global.getSettings().getString("aEP_En", id);
    }
    return Global.getSettings().getString("aEP", id);
  }

  public static String txt(String category, String id) {
    try {
      if(Global.getSettings().getBoolean("aEP_UseEnString")) {
        return Global.getSettings().getString(category.replace("aEP_","aEP_EN_"), id);
      }else {
        return Global.getSettings().getString(category, id);
      }
    } catch (Exception e){
      return "";
    }
  }

  public static ArrayList<RowData> jsonToList(JSONArray jsonArray) {
    ArrayList<RowData> dataList = new ArrayList<>();
    try {
      //JSONArray jsonArray = new JSONArray(jsonString);
      for (int i = 0; i < jsonArray.length(); i++) {
        JSONObject jsonObject = jsonArray.getJSONObject(i);
        RowData dataInOneRow = new RowData(jsonObject);
        if(dataInOneRow.getId().isEmpty()) continue;
        dataList.add(dataInOneRow);
      }
    }catch (Exception ignored){

    }
    return dataList;
  }

  public static String getValueById(ArrayList<RowData> allRows, String id, String columnName) {
    for (RowData person : allRows) {
      if (person.getId().equals(id)) {
        return (String) person.getProperty(columnName) ;
      }
    }
    return "";
  }

  public static class RowData {
    private final Map<String, String> properties;

    public RowData(JSONObject jsonObject) {
      this.properties = new HashMap<>();
      try{
        Iterator it = jsonObject.keys();
        while (it.hasNext()) {
          String key = (String) it.next();
          properties.put(key, (String) jsonObject.get(key));
        }
      }catch (Exception ignored){}
    }

    // Getters
    public String getId() {
      if(getProperty("id") == null) return "";
      return getProperty("id");
    }

    public String getProperty(String key) {
      return properties.get(key);
    }
  }


  public static class FloatDataRecorder
  {
    private float lastFrameData;
    private float accumulatedData;

    public FloatDataRecorder() {
      lastFrameData = 0f;
      accumulatedData = 0f;
    }

    public void addData(float amount) {
      accumulatedData = accumulatedData + amount;
      lastFrameData = amount;
      //engine.addFloatingText(engine.getPlayerShip().getMouseTarget(),accumulatedData + "",20f,new Color(100,100,100,100),engine.getPlayerShip(),1f,5f);
    }

    public void renewData(float thisFrameData) {
      accumulatedData = MathUtils.clamp(accumulatedData + thisFrameData - lastFrameData, 0f,Float.MAX_VALUE);

      lastFrameData = thisFrameData;
    }

    /**
     * 输入新的数据，自动计算和上一帧的差，存入差分积
     * */
    public void addRenewData(float thisFrameData) {
      if (thisFrameData > lastFrameData) {
        accumulatedData = accumulatedData + thisFrameData - lastFrameData;
      }
      //engine.addFloatingText(engine.getPlayerShip().getMouseTarget(),accumulatedData + "",20f,new Color(100,100,100,100),engine.getPlayerShip(),1f,5f);


      lastFrameData = thisFrameData;
    }

    public void subRenewData(float thisFrameData) {
      if (thisFrameData < lastFrameData) {
        accumulatedData = accumulatedData - thisFrameData + lastFrameData;
      }

      lastFrameData = thisFrameData;
    }

    public void reset() {
      accumulatedData = 0f;
      lastFrameData = 0f;
    }

    public float getTotal() {
      return accumulatedData;
    }

    public void setTotal(float total) {
      accumulatedData = total;
    }

    public float getLast() {
      return lastFrameData;
    }

    public void reduceAmount(float amount) {
      accumulatedData = accumulatedData - amount;
    }

    public void setLastFrameData(float newLast) {
      lastFrameData = newLast;
    }

  }


}

