package com.tonic.remapper.editor;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MethodFieldTable extends AbstractTableModel {
    private final Kind kind;
    private ClassMapping current;
    private String filter = "";
    private List<Integer> filteredRows = new ArrayList<>();
    private boolean showOnlyMapped = false;

    public MethodFieldTable(Kind k) { this.kind = k; }

    public void setClass(ClassMapping cm) {
        current = cm;
        updateFilteredRows();
        fireTableDataChanged();
    }

    public void setShowOnlyMapped(boolean b) {           // NEW
        showOnlyMapped = b;
        updateFilteredRows();
        fireTableDataChanged();
    }

    public void setFilter(String f) {
        if (f == null) f = "";
        filter = f.trim().toLowerCase(Locale.ROOT);
        updateFilteredRows();
        fireTableDataChanged();
    }

    private void updateFilteredRows() {
        filteredRows.clear();
        if (current == null) return;

        /* decide upfront whether a record passes the "mapped" test */
        java.util.function.Predicate<String> notBlank =
                s -> s != null && !s.isBlank();

        if (kind == Kind.METHOD) {
            for (int i = 0; i < current.methods.size(); i++) {
                MethodRecord mr = current.methods.get(i);

                if (showOnlyMapped && !notBlank.test(mr.newName)) continue; // NEW

                String obf    = (mr.node.name + mr.node.desc).toLowerCase(Locale.ROOT);
                String mapped = mr.newName != null ? mr.newName.toLowerCase(Locale.ROOT) : "";

                if (filter.isEmpty() || obf.contains(filter) || mapped.contains(filter))
                    filteredRows.add(i);
            }
        } else {
            for (int i = 0; i < current.fields.size(); i++) {
                FieldRecord fr = current.fields.get(i);

                if (showOnlyMapped && !notBlank.test(fr.newName)) continue; // NEW

                String obf    = (fr.node.name + " " + fr.node.desc).toLowerCase(Locale.ROOT);
                String mapped = fr.newName != null ? fr.newName.toLowerCase(Locale.ROOT) : "";

                if (filter.isEmpty() || obf.contains(filter) || mapped.contains(filter))
                    filteredRows.add(i);
            }
        }
    }

    @Override
    public int getRowCount() { return current == null ? 0 : filteredRows.size(); }

    @Override
    public int getColumnCount() { return 2; }

    @Override
    public String getColumnName(int col) { if (col == 0) return "Obf"; if (col == 1) return "Mapped"; return ""; }

    @Override
    public Object getValueAt(int row, int col) {
        if (current == null) return null;
        int underlying = filteredRows.get(row);
        if (kind == Kind.METHOD) {
            MethodRecord mr = current.methods.get(underlying);
            if (col == 0) return mr.node.name + mr.node.desc;
            return mr.newName;
        } else {
            FieldRecord fr = current.fields.get(underlying);
            if (col == 0) return fr.node.name + " " + fr.node.desc;
            return fr.newName;
        }
    }

    @Override
    public boolean isCellEditable(int row, int col) { return col == 1; }

    @Override
    public void setValueAt(Object aValue, int row, int col) {
        if (current == null || col != 1) return;
        int underlying = filteredRows.get(row);
        String v = aValue == null ? null : aValue.toString();
        if (kind == Kind.METHOD) {
            MethodRecord mr = current.methods.get(underlying);
            mr.newName = v;
            String key = mr.node.name + mr.node.desc;
            if (v == null || v.isBlank()) current.methodMap.remove(key);
            else current.methodMap.put(key, v);
        } else {
            FieldRecord fr = current.fields.get(underlying);
            fr.newName = v;
            String key = fr.node.name + " " + fr.node.desc;
            if (v == null || v.isBlank()) current.fieldMap.remove(key);
            else current.fieldMap.put(key, v);
        }
        fireTableRowsUpdated(row, row);
    }

    public MethodRecord getMethodRecordAt(int viewRow) {
        if (kind != Kind.METHOD || current == null) return null;
        int modelRow = filteredRows.get(viewRow);        // convert via our own mapping
        return current.methods.get(modelRow);
    }
}