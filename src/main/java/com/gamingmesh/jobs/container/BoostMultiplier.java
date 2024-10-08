package com.gamingmesh.jobs.container;

import java.util.HashMap;
import java.util.Map;

public class BoostMultiplier implements Cloneable {

    private final Map<CurrencyType, Double> map = new HashMap<>();
    private final Map<CurrencyType, Long> timers = new HashMap<>();

    @Override
    public BoostMultiplier clone() {
        BoostMultiplier boost = new BoostMultiplier();
        for (CurrencyType type : CurrencyType.values()) {
            boost.add(type, map.get(type));
        }
        return boost;
    }

    public BoostMultiplier(Map<CurrencyType, Double> map) {
        for (CurrencyType one : CurrencyType.values()) {
            this.map.put(one, map.getOrDefault(one, 0D));
        }
    }

    public BoostMultiplier() {
        for (CurrencyType one : CurrencyType.values()) {
            this.map.put(one, 0D);
        }
    }

    public BoostMultiplier add(CurrencyType type, double amount) {
        if (!Double.isNaN(amount))
            this.map.put(type, amount);
        timers.remove(type);
        return this;
    }

    public BoostMultiplier add(CurrencyType type, double amount, long time) {
        add(type, amount);
        timers.put(type, time);
        return this;
    }

    public BoostMultiplier add(double amount) {
        if (amount != 0 && !Double.isNaN(amount)) {
            for (CurrencyType one : CurrencyType.values()) {
                map.put(one, amount);
            }
        }
        return this;
    }

    public double get(CurrencyType type) {
        if (!isValid(type))
            return 0D;
        return map.getOrDefault(type, 0D);
    }

    public Long getTime(CurrencyType type) {
        return timers.get(type);
    }

    public boolean isValid(CurrencyType type) {
        Long time = getTime(type);
        if (time == null)
            return true;

        if (time < System.currentTimeMillis()) {
            map.remove(type);
            timers.remove(type);
            return false;
        }

        return true;
    }

    public void add(BoostMultiplier armorboost) {
        for (CurrencyType one : CurrencyType.values()) {
            map.put(one, get(one) + armorboost.get(one));
        }
    }
}
