package org.moss.objects;

import org.moss.Env;
import org.moss.objects.ProcList.Proc;

import java.util.List;
import java.util.Collections;
import java.util.Comparator;

public abstract class AbsTop extends AbsMossObject implements MossObject {

    public AbsTop(String var, String num) {
        this.var = var;
        this.num = new Integer(num).intValue() - 1;
    }

    public abstract Comparator getComparator();
    public abstract void setList(List<Proc> ls);
    public abstract List<Proc> getList();

    public DataProvider getDataProvider() {
        return this.procList;
    }

    @Override
    public void preDraw(Env env) {
        if (getList() != null) {
            return;
        }
        setList(procList.getProcesses());
        Collections.sort(getList(), getComparator());
    }

    @Override
    public void postDraw(Env env) {
        setList(null);
    }

    @Override
    public String toString() {
        if (null == getList()) {
            return "";
        }
        if (getList().size() <= num) {
            return "";
        }
        Proc e = getList().get(num);
        if (null == e) {
            return "";
        }
        if ("name".equals(var)) {
            return e.name;
        } else if ("package".equals(var)) {
            return e.packageName;
        } else if ("pid".equals(var)) {
            return e.pid;
        } else if ("cpu".equals(var)) {
            return String.format("%.2f", e.cpuPerc);
        } else if ("mem".equals(var)) {
            return String.format("%.2f", e.memPerc);
        } else {
            return "";
        }
    }

    protected String var;
    protected int num;

    static ProcList procList = ProcList.INSTANCE;
}
