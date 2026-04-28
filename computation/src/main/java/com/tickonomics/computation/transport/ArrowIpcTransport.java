package com.tickonomics.computation.transport;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowStreamReader;
import org.apache.arrow.vector.ipc.ArrowStreamWriter;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ArrowIpcTransport {

    private final BufferAllocator allocator;

    public ArrowIpcTransport() {
        this.allocator = new RootAllocator();
    }

    public ArrowIpcTransport(BufferAllocator allocator) {
        this.allocator = allocator;
    }

    public byte[] serializeTimeSeries(String symbol, double[] values, long[] timestamps) {
        if (values.length != timestamps.length) {
            throw new IllegalArgumentException("Values and timestamps must have the same length");
        }
        Schema schema = timeSeriesSchema();
        try (VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator)) {
            Float8Vector valuesVector = (Float8Vector) root.getVector("values");
            IntVector symbolOffsetVector = (IntVector) root.getVector("symbol_offset");
            Float8Vector tsVector = (Float8Vector) root.getVector("timestamps");
            root.setRowCount(values.length);
            valuesVector.allocateNew(values.length);
            tsVector.allocateNew(values.length);
            symbolOffsetVector.allocateNew(values.length);
            for (int i = 0; i < values.length; i++) {
                valuesVector.set(i, values[i]);
                tsVector.set(i, (double) timestamps[i]);
                symbolOffsetVector.set(i, 0);
            }
            valuesVector.setValueCount(values.length);
            tsVector.setValueCount(values.length);
            symbolOffsetVector.setValueCount(values.length);
            return writeRoot(root);
        }
    }

    public TimeSeriesBatch deserializeTimeSeries(byte[] arrowData) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(arrowData);
             ArrowStreamReader reader = new ArrowStreamReader(bais, allocator)) {
            reader.loadNextBatch();
            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            Float8Vector valuesVector = (Float8Vector) root.getVector("values");
            Float8Vector tsVector = (Float8Vector) root.getVector("timestamps");
            int rowCount = root.getRowCount();
            double[] values = new double[rowCount];
            long[] timestamps = new long[rowCount];
            for (int i = 0; i < rowCount; i++) {
                values[i] = valuesVector.get(i);
                timestamps[i] = (long) tsVector.get(i);
            }
            return new TimeSeriesBatch(values, timestamps);
        } catch (IOException e) {
            throw new ArrowTransportException("Failed to deserialize Arrow IPC data", e);
        }
    }

    public byte[] serializeAnalysisResult(Map<String, double[]> columns) {
        List<Field> fields = columns.entrySet().stream()
                .map(e -> new Field(e.getKey(), FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)), null))
                .toList();
        Schema schema = new Schema(fields);
        try (VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator)) {
            int rowCount = columns.values().iterator().next().length;
            root.setRowCount(rowCount);
            for (Map.Entry<String, double[]> entry : columns.entrySet()) {
                Float8Vector vec = (Float8Vector) root.getVector(entry.getKey());
                vec.allocateNew(rowCount);
                for (int i = 0; i < entry.getValue().length; i++) {
                    vec.set(i, entry.getValue()[i]);
                }
                vec.setValueCount(rowCount);
            }
            return writeRoot(root);
        }
    }

    private byte[] writeRoot(VectorSchemaRoot root) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ArrowStreamWriter writer = new ArrowStreamWriter(root, null, baos);
            writer.start();
            writer.writeBatch();
            writer.end();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new ArrowTransportException("Failed to serialize Arrow IPC data", e);
        }
    }

    private static Schema timeSeriesSchema() {
        return new Schema(List.of(
                new Field("values", FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)), null),
                new Field("symbol_offset", FieldType.nullable(new ArrowType.Int(32, true)), null),
                new Field("timestamps", FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)), null)
        ));
    }

    public record TimeSeriesBatch(double[] values, long[] timestamps) {
        public TimeSeriesBatch {
            if (values == null || timestamps == null) {
                throw new NullPointerException("Values and timestamps must not be null");
            }
            if (values.length != timestamps.length) {
                throw new IllegalArgumentException("Values length (" + values.length + ") must equal timestamps length (" + timestamps.length + ")");
            }
        }
    }

    public static class ArrowTransportException extends RuntimeException {
        public ArrowTransportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
