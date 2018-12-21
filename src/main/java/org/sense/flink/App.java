package org.sense.flink;

import java.util.Scanner;

import org.apache.flink.runtime.client.JobExecutionException;
import org.sense.flink.examples.batch.MatrixMultiplication;
import org.sense.flink.examples.stream.AdaptiveFilterRangeMqttEdgent;
import org.sense.flink.examples.stream.SensorsDynamicFilterMqttEdgentQEP;
import org.sense.flink.examples.stream.SensorsReadingMqttEdgentQEP;
import org.sense.flink.examples.stream.SensorsReadingMqttJoinQEP;
import org.sense.flink.examples.stream.WordCountMqttFilterQEP;
import org.sense.flink.examples.stream.WordCountSocketFilterQEP;

/**
 * 
 * @author Felipe Oliveira Gutierrez
 *
 */
public class App {
	public static void main(String[] args) throws Exception {

		try {
			int app = 0;
			do {
				System.out.println("0 - exit");
				System.out.println("1 - World count (Sokect stream) with Filter and QEP");
				System.out.println("2 - World count (MQTT stream) with Filter and QEP");
				System.out.println("3 - Matrix multiplication using batch and QEP");
				System.out.println("4 - Two fake sensors (MQTT stream) and QEP");
				System.out.println("5 - Fake sensor from Apache Edgent (MQTT stream) and QEP");
				System.out.println("6 - Dynamic filter over fake data source and QEP");
				System.out.println("7 - Adaptive filter pushed down to Apache Edgent");
				System.out.print("    Please enter which application you want to run: ");

				String msg = (new Scanner(System.in)).nextLine();
				app = Integer.valueOf(msg);
				switch (app) {
				case 0:
					System.out.println("bis später");
					break;
				case 1:
					System.out.println("App 1 selected");
					new WordCountSocketFilterQEP();
					app = 0;
					break;
				case 2:
					System.out.println("App 2 selected");
					new WordCountMqttFilterQEP();
					app = 0;
					break;
				case 3:
					System.out.println("App 3 selected");
					new MatrixMultiplication();
					app = 0;
					break;
				case 4:
					System.out.println("App 4 selected");
					new SensorsReadingMqttJoinQEP();
					app = 0;
					break;
				case 5:
					System.out.println("App 5 selected");
					new SensorsReadingMqttEdgentQEP();
					app = 0;
					break;
				case 6:
					System.out.println("App 6 selected");
					System.out.println("use 'mosquitto_pub -h 127.0.0.1 -t topic-parameter -m \"0.0,1000.0\"' ");
					System.out.println("on the terminal to change the parameters at runtime.");
					new SensorsDynamicFilterMqttEdgentQEP();
					app = 0;
					break;
				case 7:
					System.out.println("App 7 selected");
					System.out.println("The adaptive filter was pushed down to the data source");
					new AdaptiveFilterRangeMqttEdgent();
					app = 0;
					break;
				default:
					System.out.println("No application selected [" + app + "] ");
					break;
				}
			} while (app != 0);
		} catch (JobExecutionException ce) {
			System.err.println(ce.getMessage());
			ce.printStackTrace();
		}
	}
}
