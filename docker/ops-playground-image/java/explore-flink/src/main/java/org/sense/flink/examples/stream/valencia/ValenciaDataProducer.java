package org.sense.flink.examples.stream.valencia;

import org.fusesource.hawtbuf.AsciiBuffer;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtbuf.UTF8Buffer;
import org.fusesource.mqtt.client.*;
import org.sense.flink.util.ValenciaItemType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;

import static org.sense.flink.util.ValenciaMqttTopics.*;

/**
 * Usage:
 *
 * <pre>
 * java -classpath /home/flink/flink-1.9.0/lib/flink-dist_2.11-1.9.0.jar:/home/felipe/workspace-scala/explore-flink/target/explore-flink.jar org.sense.flink.App -app 32 -offlineData true &
 * java -classpath /home/flink/flink-1.9.0/lib/flink-dist_2.11-1.9.0.jar:/home/felipe/workspace-scala/explore-flink/target/explore-flink.jar org.sense.flink.App -app 33 -offlineData true &
 * </pre>
 *
 * @author Felipe Oliveira Gutierrez
 */
public class ValenciaDataProducer extends Thread {

    private static final String VALENCIA_TRAFFIC_JAM_URL = "http://mapas.valencia.es/lanzadera/opendata/Tra-estado-trafico/JSON";
    private static final String VALENCIA_POLLUTION_URL = "http://mapas.valencia.es/lanzadera/opendata/Estautomaticas/JSON";
    private static final String VALENCIA_NOISE_URL = "http://???.??";

    private static final String VALENCIA_TRAFFIC_JAM_ONLINE_FILE = "/valencia/traffic-jam-state-online.json";
    private static final String VALENCIA_TRAFFIC_JAM_OFFLINE_FILE = "/valencia/traffic-jam-state-offline.json";
    private static final String VALENCIA_TRAFFIC_JAM_OFFLINE_SKEW_FILE = "/valencia/traffic-jam-state-offline-skewed.json";
    private static final String VALENCIA_POLLUTION_ONLINE_FILE = "/valencia/air-pollution-online.json";
    private static final String VALENCIA_POLLUTION_OFFLINE_FILE = "/valencia/air-pollution-offline.json";
    private static final String VALENCIA_POLLUTION_OFFLINE_SKEW_FILE = "/valencia/air-pollution-offline-skewed.json";
    private static final String VALENCIA_NOISE_FILE = "/valencia/noise.json";
    private static final String VALENCIA_NOISE_OFFLINE_FILE = "/valencia/noise-offline.json";
    private static final String VALENCIA_NOISE_OFFLINE_SKEW_FILE = "/valencia/noise-offline-skewed.json";

    private ValenciaItemType valenciaItemType;
    private FutureConnection connection;
    private CallbackConnection connectionSideParameter;
    private URL url;
    private MQTT mqtt;
    private String host;
    private String topicToPublish;
    private String topicFrequencyParameter;
    private long count;
    private long maxCount;
    private int port;
    private int delay = 10000;
    private boolean running = false;
    private boolean offlineData;
    private boolean skewedData;

    public ValenciaDataProducer(ValenciaItemType valenciaItemType, boolean offlineData) throws MalformedURLException {
        this(valenciaItemType, "localhost", 1883, offlineData, Long.MAX_VALUE, false);
    }

    public ValenciaDataProducer(ValenciaItemType valenciaItemType, boolean offlineData, long maxCount,
                                boolean skewedData) throws MalformedURLException {
        this(valenciaItemType, "localhost", 1883, offlineData, maxCount, skewedData);
    }

    public ValenciaDataProducer(ValenciaItemType valenciaItemType, String host, boolean offlineData, long maxCount,
                                boolean skewedData) throws MalformedURLException {
        this(valenciaItemType, host, 1883, offlineData, maxCount, skewedData);
    }

    public ValenciaDataProducer(ValenciaItemType valenciaItemType, String host, int port, boolean offlineData,
                                long maxCount, boolean skewedData) throws MalformedURLException {
        try {
            this.valenciaItemType = valenciaItemType;
            this.host = host;
            this.port = port;
            this.running = true;
            this.offlineData = offlineData;
            this.count = 0L;
            this.maxCount = maxCount;
            this.skewedData = skewedData;
            if (valenciaItemType == ValenciaItemType.TRAFFIC_JAM) {
                this.topicToPublish = TOPIC_VALENCIA_TRAFFIC_JAM;
                if (this.skewedData) {
                    this.topicFrequencyParameter = TOPIC_FREQUENCY_VALENCIA_SKEW_TRAFFIC_JAM;
                } else {
                    this.topicFrequencyParameter = TOPIC_FREQUENCY_VALENCIA_TRAFFIC_JAM;
                }
                this.url = new URL(VALENCIA_TRAFFIC_JAM_URL);
            } else if (valenciaItemType == ValenciaItemType.AIR_POLLUTION) {
                this.topicToPublish = TOPIC_VALENCIA_POLLUTION;
                if (this.skewedData) {
                    this.topicFrequencyParameter = TOPIC_FREQUENCY_VALENCIA_SKEW_POLLUTION;
                } else {
                    this.topicFrequencyParameter = TOPIC_FREQUENCY_VALENCIA_POLLUTION;
                }
                this.url = new URL(VALENCIA_POLLUTION_URL);
            } else if (valenciaItemType == ValenciaItemType.NOISE) {
                this.topicToPublish = TOPIC_VALENCIA_NOISE;
                if (this.skewedData) {
                    this.topicFrequencyParameter = TOPIC_FREQUENCY_VALENCIA_SKEW_NOISE;
                } else {
                    this.topicFrequencyParameter = TOPIC_FREQUENCY_VALENCIA_NOISE;
                }
                this.url = new URL(VALENCIA_NOISE_URL);
            } else {
                System.out.println("Wrong topic to publish messages");
            }
            this.disclaimer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {

        boolean offlineData = true;
        ValenciaDataProducer producer = new ValenciaDataProducer(ValenciaItemType.TRAFFIC_JAM, offlineData, 2, false);
        // ValenciaDataProducer producer = new
        // ValenciaDataProducer(ValenciaItemType.AIR_POLLUTION, offlineData, 2);
        producer.connect();
        producer.start();
        producer.publish();
        producer.disconnect();
    }

    public void connect() throws Exception {
        mqtt = new MQTT();
        mqtt.setHost(host, port);

        connection = mqtt.futureConnection();
        connection.connect().await();
    }

    public void run() {
        connectionSideParameter = mqtt.callbackConnection();
        connectionSideParameter.listener(new org.fusesource.mqtt.client.Listener() {
            public void onConnected() {
            }

            public void onDisconnected() {
            }

            public void onFailure(Throwable value) {
                value.printStackTrace();
                System.exit(-2);
            }

            public void onPublish(UTF8Buffer topic, Buffer msg, Runnable ack) {
                String body = msg.utf8().toString();

                if (isInteger(body)) {
                    System.out.println("Reading new frequency parameter: " + body);
                    delay = Integer.parseInt(body);
                } else if ("SHUTDOWN".equalsIgnoreCase(body)) {
                    running = false;
                } else {
                    System.out.println(body);
                }
                ack.run();
            }
        });
        connectionSideParameter.connect(new Callback<Void>() {
            @Override
            public void onSuccess(Void value) {
                Topic[] topics = {new Topic(topicFrequencyParameter, QoS.AT_LEAST_ONCE)};
                connectionSideParameter.subscribe(topics, new Callback<byte[]>() {
                    public void onSuccess(byte[] qoses) {
                    }

                    public void onFailure(Throwable value) {
                        value.printStackTrace();
                        System.exit(-2);
                    }
                });
            }

            @Override
            public void onFailure(Throwable value) {
                value.printStackTrace();
                System.exit(-2);
            }
        });

        // Wait forever..
        // synchronized (ValenciaDataProducer.class) {
        // while (true)
        // ValenciaDataProducer.class.wait();
        // }
    }

    public void publish() throws Exception {
        int size = 256;

        final LinkedList<Future<Void>> queue = new LinkedList<Future<Void>>();
        UTF8Buffer topic = new UTF8Buffer(topicToPublish);

        while (running) {
            Buffer msg = new AsciiBuffer(readDataFromResource());

            // Send the publish without waiting for it to complete. This allows us
            // to send multiple message without blocking..
            queue.add(connection.publish(topic, msg, QoS.AT_LEAST_ONCE, false));

            // Eventually we start waiting for old publish futures to complete
            // so that we don't create a large in memory buffer of outgoing message.s
            if (queue.size() >= 1000) {
                queue.removeFirst().await();
            }
            Thread.sleep(delay);
            this.checkEndOfStream();
        }

        queue.add(connection.publish(topic, new AsciiBuffer("SHUTDOWN"), QoS.AT_LEAST_ONCE, false));
        while (!queue.isEmpty()) {
            queue.removeFirst().await();
        }
    }

    private void checkEndOfStream() {
        if (this.maxCount != -1) {
            this.count++;
            if (this.count >= this.maxCount) {
                this.running = false;
            }
        }
    }

    private String readDataFromResource() throws Exception {
        // get the data source file to collect data
        InputStream in = getDataSourceInputStream();

        if (!offlineData) {
            in = url.openStream();
        }
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in));
        StringBuilder builder = new StringBuilder();
        String line;
        try {
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line + "\n");
            }
            bufferedReader.close();

        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return builder.toString();
    }

    private InputStream getDataSourceInputStream() throws Exception {
        if (valenciaItemType == ValenciaItemType.TRAFFIC_JAM) {
            if (offlineData) {
                if (skewedData) {
                    return getClass().getResourceAsStream(VALENCIA_TRAFFIC_JAM_OFFLINE_SKEW_FILE);
                } else {
                    return getClass().getResourceAsStream(VALENCIA_TRAFFIC_JAM_OFFLINE_FILE);
                }
            } else {
                return getClass().getResourceAsStream(VALENCIA_TRAFFIC_JAM_ONLINE_FILE);
            }
        } else if (valenciaItemType == ValenciaItemType.AIR_POLLUTION) {
            if (offlineData) {
                if (skewedData) {
                    return getClass().getResourceAsStream(VALENCIA_POLLUTION_OFFLINE_SKEW_FILE);
                } else {
                    return getClass().getResourceAsStream(VALENCIA_POLLUTION_OFFLINE_FILE);
                }
            } else {
                return getClass().getResourceAsStream(VALENCIA_POLLUTION_ONLINE_FILE);
            }
        } else if (valenciaItemType == ValenciaItemType.NOISE) {
            if (offlineData) {
                if (skewedData) {
                    return getClass().getResourceAsStream(VALENCIA_NOISE_OFFLINE_SKEW_FILE);
                } else {
                    return getClass().getResourceAsStream(VALENCIA_NOISE_OFFLINE_FILE);
                }
            } else {
                return getClass().getResourceAsStream(VALENCIA_NOISE_FILE);
            }
        } else {
            throw new Exception("ValenciaItemType is NULL!");
        }
    }

    public void disconnect() throws Exception {
        connection.disconnect().await();
    }

    public boolean isInteger(String s) {
        return isInteger(s, 10);
    }

    public boolean isInteger(String s, int radix) {
        if (s.isEmpty())
            return false;
        for (int i = 0; i < s.length(); i++) {
            if (i == 0 && s.charAt(i) == '-') {
                if (s.length() == 1)
                    return false;
                else
                    continue;
            }
            if (Character.digit(s.charAt(i), radix) < 0)
                return false;
        }
        return true;
    }

    private void disclaimer() {
        // @formatter:off
        System.out.println("This is the application [" + ValenciaDataProducer.class.getSimpleName() + "].");
        System.out.println("It aims to collect online or offiline data from Valencia Open-data web portal and forward it to a mqtt channel with the possibility to vary the frequency of emmision.");
        System.out.println("To consume data on the terminal use:");
        System.out.println("mosquitto_sub -h " + host + " -p " + port + " -t " + topicToPublish);
        System.out.println("To change the frequency of emission use:");
        System.out.println("mosquitto_pub -h " + host + " -p " + port + " -t " + topicFrequencyParameter + " -m \"miliseconds\"");
        System.out.println();
        // @formatter:on
    }
}
