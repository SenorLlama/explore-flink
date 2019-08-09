package org.sense.flink.examples.stream;

import static org.sense.flink.util.MetricLabels.METRIC_VALENCIA_COMBINER;
import static org.sense.flink.util.MetricLabels.METRIC_VALENCIA_DISTRICT_KEY_MAP;
import static org.sense.flink.util.MetricLabels.METRIC_VALENCIA_DISTRICT_MAP;
import static org.sense.flink.util.MetricLabels.METRIC_VALENCIA_SINK;
import static org.sense.flink.util.MetricLabels.METRIC_VALENCIA_SOURCE;
import static org.sense.flink.util.MetricLabels.METRIC_VALENCIA_STRING_MAP;
import static org.sense.flink.util.MetricLabels.METRIC_VALENCIA_SYNTHETIC_FLATMAP;
import static org.sense.flink.util.MetricLabels.METRIC_VALENCIA_WINDOW;

import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.sense.flink.examples.stream.operator.impl.MapStreamBundleOperatorDynamic;
import org.sense.flink.examples.stream.trigger.impl.CountBundleTriggerDynamic;
import org.sense.flink.examples.stream.udf.MapBundleFunction;
import org.sense.flink.examples.stream.udf.impl.MapBundleValenciaImpl;
import org.sense.flink.examples.stream.udf.impl.ValenciaDistrictItemTypeAggWindow;
import org.sense.flink.examples.stream.udf.impl.ValenciaItemDistrictAsKeyMap;
import org.sense.flink.examples.stream.udf.impl.ValenciaItemDistrictMap;
import org.sense.flink.examples.stream.udf.impl.ValenciaItemKeySelector;
import org.sense.flink.examples.stream.udf.impl.ValenciaItemSyntheticData;
import org.sense.flink.examples.stream.udf.impl.ValenciaItemToStringMap;
import org.sense.flink.mqtt.MqttStringPublisher;
import org.sense.flink.pojo.Point;
import org.sense.flink.pojo.ValenciaItem;
import org.sense.flink.source.ValenciaItemConsumer;
import org.sense.flink.util.ValenciaItemType;

/**
 * 
 * @author Felipe Oliveira Gutierrez
 *
 */
public class ValenciaDataSkewedCombinerExample {

	private final String topic = "topic-valencia-data-skewed";

	public static void main(String[] args) throws Exception {
		new ValenciaDataSkewedCombinerExample("127.0.0.1", "127.0.0.1");
	}

	public ValenciaDataSkewedCombinerExample(String ipAddressSource01, String ipAddressSink) throws Exception {
		disclaimer();

		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		env.setStreamTimeCharacteristic(TimeCharacteristic.IngestionTime);

		// @formatter:off
		// Static coordinate to create synthetic data
		// Point point = new Point(725140.37, 4371855.492); // id=3, district=Extramurs
		Point point = new Point(726777.707, 4369824.436); // id=10, district=Quatre Carreres
		double distance = 1000.0; // distance in meters
		long districtId = 10;

		// Sources -> add synthetic data -> filter
		DataStream<Tuple2<Long , ValenciaItem>> streamTrafficJam = env
				.addSource(new ValenciaItemConsumer(ValenciaItemType.TRAFFIC_JAM, Time.minutes(5).toMilliseconds(), false)).name(METRIC_VALENCIA_SOURCE)
				.map(new ValenciaItemDistrictMap()).name(METRIC_VALENCIA_DISTRICT_MAP)
				.flatMap(new ValenciaItemSyntheticData(ValenciaItemType.TRAFFIC_JAM, point, distance, districtId)).name(METRIC_VALENCIA_SYNTHETIC_FLATMAP)
				.map(new ValenciaItemDistrictAsKeyMap()).name(METRIC_VALENCIA_DISTRICT_KEY_MAP)
				;
		DataStream<Tuple2<Long , ValenciaItem>> streamAirPollution = env
				.addSource(new ValenciaItemConsumer(ValenciaItemType.AIR_POLLUTION, Time.minutes(30).toMilliseconds(), false)).name(METRIC_VALENCIA_SOURCE)
				.map(new ValenciaItemDistrictMap()).name(METRIC_VALENCIA_DISTRICT_MAP)
				.flatMap(new ValenciaItemSyntheticData(ValenciaItemType.AIR_POLLUTION, point, distance, districtId)).name(METRIC_VALENCIA_SYNTHETIC_FLATMAP)
				.map(new ValenciaItemDistrictAsKeyMap()).name(METRIC_VALENCIA_DISTRICT_KEY_MAP)
				;

		// Combiner
		MapBundleFunction<Long, ValenciaItem, Tuple2<Long, ValenciaItem>, Tuple2<Long, ValenciaItem>> myMapBundleFunction = new MapBundleValenciaImpl();
		// CountBundleTrigger<Tuple2<Long, ValenciaItem>> bundleTrigger = new CountBundleTrigger<Tuple2<Long, ValenciaItem>>(100);
		CountBundleTriggerDynamic<Long, Tuple2<Long, ValenciaItem>> bundleTrigger = new CountBundleTriggerDynamic<Long, Tuple2<Long, ValenciaItem>>();
		KeySelector<Tuple2<Long, ValenciaItem>, Long> keyBundleSelector = (KeySelector<Tuple2<Long, ValenciaItem>, Long>) value -> value.f0;
		TypeInformation<Tuple2<Long, ValenciaItem>> info = TypeInformation.of(new TypeHint<Tuple2<Long, ValenciaItem>>(){});

		// Union -> Combiner -> Average -> Print
		streamTrafficJam.union(streamAirPollution)
				// .transform(METRIC_VALENCIA_COMBINER, info, new MapStreamBundleOperator<>(myMapBundleFunction, bundleTrigger, keyBundleSelector)).name(METRIC_VALENCIA_COMBINER)
				.transform(METRIC_VALENCIA_COMBINER, info, new MapStreamBundleOperatorDynamic<>(myMapBundleFunction, bundleTrigger, keyBundleSelector)).name(METRIC_VALENCIA_COMBINER)
				.keyBy(new ValenciaItemKeySelector())
				.window(TumblingProcessingTimeWindows.of(Time.seconds(60)))
				.apply(new ValenciaDistrictItemTypeAggWindow()).name(METRIC_VALENCIA_WINDOW)
				.map(new ValenciaItemToStringMap()).name(METRIC_VALENCIA_STRING_MAP)
				.addSink(new MqttStringPublisher(ipAddressSink, topic)).name(METRIC_VALENCIA_SINK)
				// .print()
				;

		env.execute(ValenciaDataSkewedCombinerExample.class.getName());
		// @formatter:on
	}

	private void disclaimer() {
		System.out.println("Disclaimer...");
	}
}