package com.ctc.interlocking;

import com.ctc.model.SignalState;
import com.ctc.model.SignalState.Aspect;
import com.ctc.model.SwitchState;
import com.ctc.model.SwitchState.Position;
import com.ctc.model.TrackCircuitState;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class InterlockingConfig {

    public static final List<String> TRACK_CIRCUIT_IDS = List.of(
            "1G", "2G", "3G", "4G", "IAG", "IBG", "IIG", "IIIG", "1DG", "2DG", "3DG", "4DG"
    );

    public static final List<String> SIGNAL_IDS = List.of(
            "X", "S", "X1", "S1", "X2", "S2", "X3", "S3"
    );

    public static final List<String> SWITCH_IDS = List.of(
            "1#", "2#", "3#", "5#", "7#", "9#"
    );

    public InterlockingEngine buildEngine() {
        InterlockingEngine engine = new InterlockingEngine();

        for (String id : TRACK_CIRCUIT_IDS) {
            engine.registerTrackCircuit(new TrackCircuitState(id, id, false, false));
        }

        for (String id : SIGNAL_IDS) {
            String name = id.startsWith("X") ? "进站信号" + id : "出站信号" + id;
            engine.registerSignal(new SignalState(id, name, Aspect.RED, false));
        }

        for (String id : SWITCH_IDS) {
            engine.registerSwitch(new SwitchState(id, id, Position.NORMAL, false));
        }

        engine.registerRoute(new RouteDefinition(
                "Route1", "X→1G 正线接车", "X",
                List.of("IAG", "1DG", "1G"),
                Map.of("1#", 0, "3#", 0),
                Set.of("Route2", "Route5")
        ));

        engine.registerRoute(new RouteDefinition(
                "Route2", "X→3G 侧线接车", "X",
                List.of("IAG", "2DG", "3G"),
                Map.of("1#", 1, "2#", 1, "5#", 0),
                Set.of("Route1", "Route5")
        ));

        engine.registerRoute(new RouteDefinition(
                "Route3", "1G→S 正线发车", "S1",
                List.of("1G", "1DG", "IBG"),
                Map.of("1#", 0, "3#", 0, "7#", 0),
                Set.of("Route4")
        ));

        engine.registerRoute(new RouteDefinition(
                "Route4", "3G→S3 侧线发车", "S3",
                List.of("3G", "2DG", "3DG", "IBG"),
                Map.of("2#", 1, "5#", 0, "9#", 1),
                Set.of("Route3")
        ));

        engine.registerRoute(new RouteDefinition(
                "Route5", "X1→IAG 接近", "X1",
                List.of("IAG"),
                Map.of("1#", 0),
                Set.of("Route1", "Route2", "Route6")
        ));

        engine.registerRoute(new RouteDefinition(
                "Route6", "X2→IIG 接近", "X2",
                List.of("IIG"),
                Map.of("3#", 1),
                Set.of("Route5")
        ));

        return engine;
    }
}
