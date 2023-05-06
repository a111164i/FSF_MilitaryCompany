package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.campaign.ui.marketinfo.m;
import combat.util.aEP_Tool;

import java.util.LinkedHashMap;
import java.util.Map;

public class aEP_NeiBoMainAnimation implements EveryFrameWeaponEffectPlugin
{

  Map<Integer, WeaponAPI> toMove = new LinkedHashMap<>();
  String id = "aEP_NeiBoMainAnimation";
  String COVER_ID = "aEP_ftr_ut_maodian_cover";

  @Override
  public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

    if (weapon.getShip() == null || engine.isPaused()) return;
    ShipAPI ship = weapon.getShip();
    for (ShipAPI m : ship.getChildModulesCopy()) {
      if (m.getStationSlot() == null || !m.isAlive()) continue;
      if (m.getStationSlot().getId().contains("TR_MOD")){
        m.setFacing(weapon.getCurrAngle());
      }
    }

    for (WeaponAPI w : ship.getAllWeapons()){
      if(!w.getSlot().isDecorative()) continue;
      if(w.getSpec().getWeaponId().equals(COVER_ID)){
        w.setCurrAngle(weapon.getCurrAngle());
      }
    }



    //Global.getLogger(this.getClass()).info("TRMOD"+ship.getChildModulesCopy().size());

    /*
    if(weapon==null||weapon.getShip()==null)return;
    WeaponAPI main=null;
    toMove.clear();
    for (WeaponAPI w : weapon.getShip().getAllWeapons())
    {
      String slotId = w.getSlot().getId();
      if (!slotId.contains("COVER")){continue;}
      if(slotId.contains("MAIN")) {main=w;continue;}
      if(slotId.equals("COVER")) {weapon=w;continue;}
      int num = Integer.parseInt(slotId.replace("COVER",""));
      toMove.put(num-1,w);
    }

    if (main==null||weapon == null|| Global.getCombatEngine().isPaused()) return;
    weapon.setCurrAngle(main.getCurrAngle());
    for (int i = 0; i < 10; i++)
      if (toMove.containsKey(i))
      {
        WeaponAPI w = toMove.get(i);
        WeaponSlotAPI slot = w.getSlot();
        Vector2f center = weapon.getLocation();
        Vector2f point = weapon.getFirePoint(i);
        //ship.getFacing()是以东为0度，北为90度，无负值
        //VectorUtils.getAngle()得到的角度与战场坐标一致
        float x = VectorUtils.getAngle(weapon.getLocation(), weapon.getFirePoint(i)) - weapon.getShip().getFacing();
        Vector2f dist = new Vector2f(point.x - center.x, point.y - center.y);
        float y = (float) Math.sqrt((double) (dist.getX() * dist.getX() + dist.getY() * dist.getY()));
        float relDistX = y * (float) FastTrig.cos(Math.toRadians(x));
        float relDistY = y * (float) FastTrig.sin(Math.toRadians(x));
        Vector2f weaponSlotRelPos = weapon.getSlot().getLocation();
        slot.getLocation().set(weaponSlotRelPos.x + relDistX, weaponSlotRelPos.y + relDistY);//FirePoint是绝对位置
        //slot.setAngle(main.getCurrAngle() - weapon.getShip().getFacing());
        //weapon.getLocation().set(w.getLocation());
        w.setCurrAngle(main.getCurrAngle());
        w.ensureClonedSpec();

      }

    //进入战场的variant并不是装配页面的variant
    //改变spec是会改变全局的，不要动
    //结束战斗时会把战斗的variant改在装配的variant上面
    //通过移除武器会导致进战场缺武器，返回装配页面不缺
     */
  }


}
