package data.scripts.shipsystems.aEP_system;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import data.scripts.weapons.aEP_DecoAnimation;
import org.lazywizard.lazylib.MathUtils;

import static data.scripts.utils.aEP_DataTool.txt;

//不知道为什么，堡垒盾的ai非得叫这个类名才会用，为了防止和原版的重了，放在另外一个目录
public class FortressShieldStats extends BaseShipSystemScript {

    public static float DAMAGE_REDUCE_MULT = 0.85f;
    public static float BONUS_ARC = 120f;

    aEP_DecoAnimation cover = null;
    aEP_DecoAnimation arm_l = null;
    aEP_DecoAnimation arm_r = null;
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        //不知道为什么，必须把这2个修改放前面，ai才会用，所有的impl效果都要写在后面
        //推断可能是在ai类中使用一个虚拟的shipAPI调用apply方法来计算护盾实际承受伤害，如果先进行null检测，可能得不到结果所以ai不用
        stats.getShieldUnfoldRateMult().modifyFlat(id, effectLevel);
        stats.getShieldDamageTakenMult().modifyMult(id, 1f - DAMAGE_REDUCE_MULT * effectLevel);
        stats.getShieldUpkeepMult().modifyMult(id, 1f- effectLevel);


        //复制粘贴
        if(stats == null || stats.getEntity() == null || !(stats.getEntity() instanceof ShipAPI)) return;
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if(ship.getShield() == null) return;

        updateDecos(ship, effectLevel);

        //战斗中动态修改arc是不会生效的
        float baseRad = ship.getMutableStats().getShieldArcBonus().computeEffective(ship.getHullSpec().getShieldSpec().getArc());
        ship.getShield().setArc(MathUtils.clamp(baseRad + BONUS_ARC * effectLevel,0f,360f));

//        float shieldCenterX = ship.getHullSpec().getShieldSpec().getCenterX();
//        float shieldCenterY = ship.getHullSpec().getShieldSpec().getCenterY();
//        ship.getShield().setCenter(shieldCenterX - 100f * effectLevel, shieldCenterY );
//
//        float shieldRadius = ship.getHullSpec().getShieldSpec().getRadius();
//        ship.getShield().setRadius(shieldRadius + 100f* effectLevel);
//
//        ship.getShield().forceFacing(ship.getFacing());

    }

    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getShieldUnfoldRateMult().unmodify(id);
        stats.getShieldDamageTakenMult().unmodify(id);
        stats.getShieldUpkeepMult().unmodify(id);


        //复制粘贴
        if(stats == null || stats.getEntity() == null || !(stats.getEntity() instanceof ShipAPI)) return;
        ShipAPI ship = (ShipAPI) stats.getEntity();

        if(ship.getShield() == null) return;
        float baseRad = ship.getMutableStats().getShieldArcBonus().computeEffective(ship.getHullSpec().getShieldSpec().getArc());
        ship.getShield().setArc(baseRad);
//        float shieldCenterX = ship.getHullSpec().getShieldSpec().getCenterX();
//        float shieldCenterY = ship.getHullSpec().getShieldSpec().getCenterY();
//        ship.getShield().setCenter(shieldCenterX, shieldCenterY );
//
//        float shieldRadius = ship.getHullSpec().getShieldSpec().getRadius();
//        ship.getShield().setRadius(shieldRadius);
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData(String.format(txt("aEP_FortressShieldStats01"),(int)(DAMAGE_REDUCE_MULT*100f * effectLevel) + "%"), false);
        }
//
        return null;
    }

    public void updateDecos(ShipAPI ship, Float effectiveLevel){
        if(cover == null || arm_l ==null || arm_r == null){
            for(WeaponAPI w : ship.getAllWeapons()){
                if(!w.isDecorative()) continue;
                if(w.getSpec().getWeaponId().equals("aEP_fga_wanliu_cover")){
                    cover = (aEP_DecoAnimation) w.getEffectPlugin();
                    continue;
                }
                if(w.getSpec().getWeaponId().equals("aEP_fga_wanliu_arm_l")){
                    arm_l = (aEP_DecoAnimation) w.getEffectPlugin();
                    continue;
                }
                if(w.getSpec().getWeaponId().equals("aEP_fga_wanliu_arm_r")){
                    arm_r = (aEP_DecoAnimation) w.getEffectPlugin();
                    continue;
                }
            }
        }

        if (cover == null || arm_l ==null || arm_r == null) return;
        cover.setMoveToLevel(effectiveLevel);
        float verticalLevel = 0f;
        float sideLevel = 1f;
        if(effectiveLevel < 0.2f){
            sideLevel = effectiveLevel/0.2f;
        }else {
            verticalLevel = (effectiveLevel - 0.2f)/0.8f;
        }
        arm_l.setMoveToLevel(verticalLevel);
        arm_l.setMoveToSideLevel(sideLevel);
        arm_r.setMoveToLevel(verticalLevel);
        arm_r.setMoveToSideLevel(sideLevel);
    }
}
