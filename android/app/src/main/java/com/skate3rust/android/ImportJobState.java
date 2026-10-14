package com.skate3rust.android;

/** Thread-safe job snapshot shared by the foreground service and setup screen. */
final class ImportJobState {
    static final class Snapshot {
        final String id,item,message;
        final ImportModel.Phase phase;
        final long completed,total;
        Snapshot(String id,ImportModel.Phase phase,long completed,long total,String item,String message) {
            this.id=id;this.phase=phase;this.completed=completed;this.total=total;this.item=item;this.message=message;
        }
        boolean running(){return phase!=ImportModel.Phase.IDLE&&!terminal(phase);}
    }
    private Snapshot value=new Snapshot("",ImportModel.Phase.IDLE,0,-1,"","");
    synchronized boolean start(String id,ImportModel.Phase phase) {
        if(value.running())return false;
        value=new Snapshot(id,phase,0,-1,"","");return true;
    }
    synchronized void report(ImportModel.Phase phase,long completed,long total,String item) {
        if(!value.running())return;
        value=new Snapshot(value.id,phase,Math.max(0,completed),total,item==null?"":item,value.message);
    }
    synchronized void finish(ImportModel.Phase phase,String message) {
        if(!terminal(phase))throw new IllegalArgumentException("Job must finish in a terminal phase");
        value=new Snapshot(value.id,phase,value.completed,value.total,value.item,message==null?"":message);
    }
    synchronized Snapshot snapshot(){return value;}
    static Snapshot restore(String id,ImportModel.Phase phase,long completed,long total,String item) {
        return new Snapshot(id,terminal(phase)?phase:ImportModel.Phase.INTERRUPTED,Math.max(0,completed),total,item==null?"":item,"Import stopped when Android closed the worker. Retry when ready.");
    }
    static boolean terminal(ImportModel.Phase phase) {
        return phase==ImportModel.Phase.COMPLETE||phase==ImportModel.Phase.CANCELLED||phase==ImportModel.Phase.FAILED||phase==ImportModel.Phase.INTERRUPTED;
    }
}
