package org.mymedialite.eval.measures;


import org.mymedialite.data.IEntityMapping;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public interface IMeasureDump extends IMeasure{

    void dump();


    public static class PerUserMeasureDump implements IMeasureDump {

        private final IMeasure measure;

        private final Map<String, Double> dump = new ConcurrentHashMap<>();

        private final IEntityMapping userMapping;

        private final File dumpLocation;

        public PerUserMeasureDump(IEntityMapping userMapping, IMeasure measure, File dumpLocation) {
            this.userMapping = userMapping;
            this.measure = measure;
            this.dumpLocation = dumpLocation;
        }

        @Override
        public double compute(Integer userId, List<Integer> recommendations, Set<Integer> correctItems, Collection<Integer> ignoreItems) {
            double v = measure.compute(userId, recommendations, correctItems, ignoreItems);
            dump.put(userMapping.toOriginalID(userId), v);
            return v;
        }

        @Override
        public boolean intermediateCalculationAllowed() {
            return measure.intermediateCalculationAllowed();
        }

        @Override
        public double normalize(double result, double factor) {
            return measure.normalize(result, factor);
        }

        @Override
        public String getName() {
            return measure.getName();
        }

        @Override
        public void dump() {
            List<String> sortedUIds = new ArrayList<>(dump.keySet());
            Collections.sort(sortedUIds);
            dumpLocation.mkdirs();
            File f = new File(dumpLocation, measure.getName() + ".csv");
            try(PrintWriter writer = new PrintWriter(new FileWriter(f, false))){
                writer.println("user_id,value");
                for (String l : sortedUIds){
                    double v = dump.get(l);
                    String outString = l + "," + Double.toString(v);
                    writer.println(outString);
                }
            } catch (IOException e){
                throw new RuntimeException(e);
            }
        }
    }
}