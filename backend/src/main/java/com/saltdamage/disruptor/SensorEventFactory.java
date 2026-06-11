package com.saltdamage.disruptor;

import com.lmax.disruptor.EventFactory;

public class SensorEventFactory implements EventFactory<SensorEvent> {

    @Override
    public SensorEvent newInstance() {
        return new SensorEvent();
    }
}
