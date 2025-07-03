package ru.rdc.PrintTalon.services;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class PrintStatsService {
    private static final Logger logger = LoggerFactory.getLogger(PrintStatsService.class);

    private final WriteApi writeApi;
    private final String bucket;
    private final String org;

    public PrintStatsService(InfluxDBClient influxClient,
                             @Value("${influx.bucket}") String bucket,
                             @Value("${influx.org}") String org) {
        this.writeApi = influxClient.makeWriteApi();
        this.bucket = bucket;
        this.org = org;
    }

    public void logPrint(String docType, Map<String, Object> params) {
        try {
            Point point = Point.measurement("prints")
                    .addTag("doc_type", docType)
                    .addFields(params)
                    .time(Instant.now(), WritePrecision.MS);

            writeApi.writePoint(bucket, org, point);
            logger.info("Successfully written to InfluxDB: {} - {}", docType, params);
        } catch (Exception e) {
            logger.error("Error writing to InfluxDB", e);
            throw new RuntimeException("Failed to write to InfluxDB", e);
        }
    }
}