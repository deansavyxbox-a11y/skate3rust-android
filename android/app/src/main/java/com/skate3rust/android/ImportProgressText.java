package com.skate3rust.android;

import java.util.Locale;

/** Formats measured transfer progress separately from conversion work without a byte estimate. */
final class ImportProgressText {
    static boolean shouldRefresh(String key) {
        return "phase".equals(key)||"completed".equals(key)||"total".equals(key)
            ||"item".equals(key)||"map_progress".equals(key)||"message".equals(key);
    }

    static String mapContext(String previous,String item) {
        if(item!=null&&item.startsWith("Converting map "))return item;
        if(item!=null&&item.startsWith("Preparing full skate park layout"))return "";
        return previous==null?"":previous;
    }

    static String render(ImportModel.Phase phase,long done,long total,String item,String mapProgress) {
        StringBuilder text=new StringBuilder("Import ").append(phase.name().toLowerCase(Locale.ROOT).replace('_',' '));
        if(total>0) {
            text.append(" · ").append(String.format(Locale.ROOT,"%.1f MB / %.1f MB",
                done/1000000.0,total/1000000.0));
        } else if(phase==ImportModel.Phase.CONVERTING) {
            text.append(" game assets");
        }
        if(phase==ImportModel.Phase.CONVERTING&&mapProgress!=null&&!mapProgress.isEmpty())
            text.append('\n').append(mapProgress);
        if(item!=null&&!item.isEmpty()&&!item.equals(mapProgress))text.append('\n').append(item);
        text.append("\nKeep the app open while files are checked.");
        return text.toString();
    }

    private ImportProgressText() {}
}
