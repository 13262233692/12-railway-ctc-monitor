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

    private TrackTopologyGraph topologyGraph;
    private ConflictPredictionEngine conflictPredictionEngine;

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

        this.topologyGraph = buildTopologyGraph();
        this.conflictPredictionEngine = new ConflictPredictionEngine(
                topologyGraph,
                engine.getTrackCircuits(),
                engine.getSwitches(),
                engine.getRouteDefinitions(),
                engine.getActiveRoutes(),
                engine.getApproachLockedRoutes()
        );
        engine.setConflictPredictionEngine(conflictPredictionEngine);

        return engine;
    }

    public static TrackTopologyGraph buildTopologyGraph() {
        TrackTopologyGraph graph = new TrackTopologyGraph();

        graph.addNode("IAG", 215, 250);
        graph.addNode("IBG", 845, 250);
        graph.addNode("1G", 530, 200);
        graph.addNode("2G", 530, 300);
        graph.addNode("3G", 530, 370);
        graph.addNode("4G", 530, 430);
        graph.addNode("IIG", 215, 200);
        graph.addNode("IIIG", 845, 200);
        graph.addNode("1DG", 405, 225);
        graph.addNode("2DG", 405, 310);
        graph.addNode("3DG", 655, 310);
        graph.addNode("4DG", 655, 365);

        graph.addBidirectionalEdge("IAG", "1DG");
        graph.addBidirectionalEdge("IAG", "2DG");
        graph.addBidirectionalEdge("1DG", "1G");
        graph.addBidirectionalEdge("2DG", "3G");
        graph.addBidirectionalEdge("1G", "1DG");
        graph.addBidirectionalEdge("3G", "2DG");
        graph.addBidirectionalEdge("1G", "IBG");
        graph.addBidirectionalEdge("3G", "3DG");
        graph.addBidirectionalEdge("3DG", "IBG");
        graph.addBidirectionalEdge("IIG", "1G");
        graph.addBidirectionalEdge("1G", "IIIG");
        graph.addBidirectionalEdge("2G", "IBG");
        graph.addBidirectionalEdge("2G", "IAG");
        graph.addBidirectionalEdge("4G", "4DG");
        graph.addBidirectionalEdge("4DG", "IBG");
        graph.addBidirectionalEdge("1DG", "2DG");
        graph.addBidirectionalEdge("3DG", "4DG");

        return graph;
    }

    public TrackTopologyGraph getTopologyGraph() {
        return topologyGraph;
    }

    public ConflictPredictionEngine getConflictPredictionEngine() {
        return conflictPredictionEngine;
    }
}
