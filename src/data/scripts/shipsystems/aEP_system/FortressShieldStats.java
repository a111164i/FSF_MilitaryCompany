package data.scripts.shipsystems.aEP_system;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.lazywizard.lazylib.MathUtils;

import static combat.util.aEP_DataTool.txt;

//不知道为什么，堡垒盾的ai非得叫这个类名才会用，为了防止和原版的重了，放在另外一个目录
public class FortressShieldStats extends BaseShipSystemScript {

    public static float DAMAGE_REDUCE_MULT = 0.8f;
    public static float BONUS_ARC = -90f;

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        //不知道为什么，必须把这2个修改放前面，ai才会用，所有的impl效果都要写在后面
        //推断可能是在ai类中使用一个虚拟的shipAPI调用apply方法来计算护盾实际承受伤害，如果先进行null检测，可能得不到结果所以ai不用
        stats.getShieldUnfoldRateMult().modifyFlat(id, 1f);
        stats.getShieldDamageTakenMult().modifyMult(id, 1f - DAMAGE_REDUCE_MULT * effectLevel);
        stats.getShieldUpkeepMult().modifyMult(id, 0f);


        //复制粘贴
        if(stats == null || stats.getEntity() == null || !(stats.getEntity() instanceof ShipAPI)) return;
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if(ship.getShield() == null) return;

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
        //复制粘贴
        if(stats == null || stats.getEntity() == null || !(stats.getEntity() instanceof ShipAPI)) return;
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if(ship.getShield() == null) return;

        float baseRad = ship.getMutableStats().getShieldArcBonus().computeEffective(ship.getHullSpec().getShieldSpec().getArc());
        ship.getShield().setArc(baseRad);
        stats.getShieldUnfoldRateMult().unmodify(id);
        stats.getShieldDamageTakenMult().unmodify(id);
        stats.getShieldUpkeepMult().unmodify(id);

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
}
