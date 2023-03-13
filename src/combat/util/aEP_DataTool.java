//by a111164
package combat.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;

import java.util.ArrayList;
import java.util.List;

public class aEP_DataTool
{
  CombatEngineAPI engine;

  public static String txt(String id) {
    return Global.getSettings().getString("aEP", id);
  }

  public static class floatDataRecorder
  {
    private float lastFrameData;
    private float accumulatedData;

    public floatDataRecorder() {
      lastFrameData = 0f;
      accumulatedData = 0f;
    }

    public void addData(float amount) {
      accumulatedData = accumulatedData + amount;
      lastFrameData = amount;
      //engine.addFloatingText(engine.getPlayerShip().getMouseTarget(),accumulatedData + "",20f,new Color(100,100,100,100),engine.getPlayerShip(),1f,5f);
    }

    public void renewData(float thisFrameData) {
      accumulatedData = aEP_Tool.limitToTop(accumulatedData + thisFrameData - lastFrameData, 9999999f, 0f);

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

  public static class Polynomial
  {
    List<Float> terms = new ArrayList<Float>();

    public Polynomial(String terms) {
      String[] id = terms.split(",");
      int num = 0;
      while (num + 1 <= id.length) {
        this.terms.add(num, Float.parseFloat(id[num]));
        num = num + 1;
      }
    }

    public float normalization(float max, float min, float X) {
      float output = getY(X);
      output = aEP_Tool.limitToTop(output, max, min);
      return (output - min) / (max - min);

    }

    public float getY(float X) {
      float outPut = 0f;
      int num = 0;
      while (num + 1 <= terms.size()) {
        outPut = (float) (outPut + terms.get(num) * Math.pow(X, num));
        num = num + 1;
      }
      return outPut;
    }


  }
}

