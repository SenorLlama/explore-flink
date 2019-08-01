package org.sense.flink.source;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.streaming.api.functions.source.RichSourceFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.sense.flink.pojo.Point;
import org.sense.flink.pojo.ValenciaItem;
import org.sense.flink.pojo.ValenciaPollution;
import org.sense.flink.pojo.ValenciaTraffic;
import org.sense.flink.util.ValenciaItemType;

/**
 * <pre>
 * The data comes from http://gobiernoabierto.valencia.es/en/dataset/?id=estado-trafico-tiempo-real and the attributes are:
 * 
 * Idtramo: Identificador único del tramo.
 * Denominacion: Denominación del tramo.
 * Estado: Estado del tráfico en tiempo real del tramo. Los códigos de los estados del tráfico son los siguientes:
 * 0 Fluido
 * 1 Denso
 * 2 Congestionado
 * 3 Cortado
 * </pre>
 * 
 * @author Felipe Oliveira Gutierrez
 *
 */
public class ValenciaItemConsumer extends RichSourceFunction<ValenciaItem> {

	private static final long serialVersionUID = 8320419468972434516L;
	private static final String VALENCIA_TRAFFIC_JAM_URL = "http://mapas.valencia.es/lanzadera/opendata/Tra-estado-trafico/JSON";
	private static final String VALENCIA_POLLUTION_URL = "http://mapas.valencia.es/lanzadera/opendata/Estautomaticas/JSON";
	private static final String VALENCIA_NOISE_URL = "";
	private static final String VALENCIA_TRAFFIC_JAM_FILE = "resources/valencia/traffic-jam-state.json";
	private static final String VALENCIA_POLLUTION_FILE = "resources/valencia/air-pollution.json";
	private static final String VALENCIA_NOISE_FILE = "resources/valencia/noise.json";
	private static final long DEFAULT_FREQUENCY_DELAY = 10000;
	private String json;
	private long frequency;
	private Time timeout;
	private ValenciaItemType valenciaItemType;

	public ValenciaItemConsumer(ValenciaItemType valenciaItemType, Time timeout) throws Exception {
		if (valenciaItemType == ValenciaItemType.TRAFFIC) {
			this.json = VALENCIA_TRAFFIC_JAM_URL;
		} else if (valenciaItemType == ValenciaItemType.AIR_POLLUTION) {
			this.json = VALENCIA_POLLUTION_URL;
		} else if (valenciaItemType == ValenciaItemType.NOISE) {
			this.json = VALENCIA_NOISE_URL;
		} else {
			throw new Exception("ValenciaItemType is NULL!");
		}
		this.valenciaItemType = valenciaItemType;
		this.frequency = DEFAULT_FREQUENCY_DELAY;
		this.timeout = timeout;
	}

	public ValenciaItemConsumer(ValenciaItemType valenciaItemType, String json, Time timeout) throws Exception {
		this(valenciaItemType, json, DEFAULT_FREQUENCY_DELAY, Time.seconds(1));
	}

	public ValenciaItemConsumer(ValenciaItemType valenciaItemType, String json, long frequency, Time timeout)
			throws Exception {
		if (valenciaItemType == null) {
			throw new Exception("ValenciaItemType is NULL!");
		}
		this.valenciaItemType = valenciaItemType;
		this.json = json;
		this.frequency = frequency;
		this.timeout = timeout;
	}

	@Override
	public void run(SourceContext<ValenciaItem> ctx) throws Exception {
		URL url = new URL(this.json);

		while (true) {
			File realTimeData = null;
			if (valenciaItemType == ValenciaItemType.TRAFFIC) {
				realTimeData = new File(VALENCIA_TRAFFIC_JAM_FILE);
			} else if (valenciaItemType == ValenciaItemType.AIR_POLLUTION) {
				realTimeData = new File(VALENCIA_POLLUTION_FILE);
			} else if (valenciaItemType == ValenciaItemType.NOISE) {
				realTimeData = new File(VALENCIA_NOISE_FILE);
			} else {
				throw new Exception("ValenciaItemType is NULL!");
			}

			// check if the file is older than 5 minutes
			boolean isNew = FileUtils.isFileNewer(realTimeData,
					Calendar.getInstance().getTimeInMillis() - timeout.toMilliseconds());
			if (!isNew) {
				InputStream is = url.openStream();
				Files.copy(is, realTimeData.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}

			BufferedReader bufferedReader = Files.newBufferedReader(realTimeData.toPath());
			StringBuilder builder = new StringBuilder();
			String line;
			try {
				while ((line = bufferedReader.readLine()) != null) {
					builder.append(line + "\n");
				}
				bufferedReader.close();

				ObjectMapper mapper = new ObjectMapper();
				JsonNode actualObj = mapper.readTree(builder.toString());
				List<Point> points = new ArrayList<Point>();

				boolean isCRS = actualObj.has("crs");
				boolean isFeatures = actualObj.has("features");
				String typeCSR = "";

				if (isCRS) {
					ObjectNode objectNodeCsr = (ObjectNode) actualObj.get("crs");
					ObjectNode objectNodeProperties = (ObjectNode) objectNodeCsr.get("properties");
					typeCSR = objectNodeProperties.get("name").asText();
					typeCSR = typeCSR.substring(typeCSR.indexOf("EPSG")).replace("::", ":");
				} else {
					System.out.println("Wrong CoordinateReferenceSystem (CSR) type");
				}

				if (isFeatures) {
					ArrayNode arrayNodeFeatures = (ArrayNode) actualObj.get("features");
					for (JsonNode jsonNode : arrayNodeFeatures) {
						JsonNode nodeProperties = jsonNode.get("properties");
						JsonNode nodeGeometry = jsonNode.get("geometry");
						ArrayNode arrayNodeCoordinates = (ArrayNode) nodeGeometry.get("coordinates");

						ValenciaItem valenciaItem;
						if (valenciaItemType == ValenciaItemType.TRAFFIC) {
							for (JsonNode coordinates : arrayNodeCoordinates) {
								ArrayNode xy = (ArrayNode) coordinates;
								points.add(new Point(xy.get(0).asDouble(), xy.get(1).asDouble(), typeCSR));
							}
							valenciaItem = new ValenciaTraffic(null, null, "", new Date(), points,
									nodeProperties.get("estado").asInt());
						} else if (valenciaItemType == ValenciaItemType.AIR_POLLUTION) {
							String p = arrayNodeCoordinates.get(0).asText() + ","
									+ arrayNodeCoordinates.get(1).asText();
							points = Point.extract(p, typeCSR);
							valenciaItem = new ValenciaPollution(null, null, "", new Date(), points,
									nodeProperties.get("mediciones").asText());
						} else if (valenciaItemType == ValenciaItemType.NOISE) {
							throw new Exception("ValenciaItemType NOISE is not implemented!");
						} else {
							throw new Exception("ValenciaItemType is NULL!");
						}
						ctx.collect(valenciaItem);
					}
				}
			} catch (IOException ioe) {
				ioe.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				Thread.sleep(this.frequency);
			}
		}
	}

	@Override
	public void cancel() {
	}

	public static void main(String[] args) {
		System.out.println(Time.minutes(20).toMilliseconds());
		System.out.println((1000 * 60 * 20));
	}
}
