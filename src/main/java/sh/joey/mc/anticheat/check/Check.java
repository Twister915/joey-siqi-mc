package sh.joey.mc.anticheat.check;

import io.reactivex.rxjava3.core.Observable;
import sh.joey.mc.anticheat.Detection;

public interface Check {

    String getName();

    Observable<Detection> detections();
}
