package edu.rutgers.brange.speedlimitwidget;

import android.provider.BaseColumns;

public class SettingsContract {

    static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + ContractEntry.TABLE_NAME + " (" +
                    ContractEntry._ID + " INTEGER PRIMARY KEY," +
                    ContractEntry.COLUMN_NAME_FACTOR + " REAL," +
                    ContractEntry.COLUMN_NAME_X + " INTEGER," +
                    ContractEntry.COLUMN_NAME_Y + " INTEGER)";

    static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + ContractEntry.TABLE_NAME;

    /* Inner class that defines the table contents */
    static class ContractEntry implements BaseColumns {
        static final String TABLE_NAME = "settings";
        static final String COLUMN_NAME_FACTOR = "factor";
        static final String COLUMN_NAME_X = "x";
        static final String COLUMN_NAME_Y = "y";
    }
}