package edu.rutgers.brange.speedlimitwidget;

import android.provider.BaseColumns;

public class SpeedAverageContract {
    static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + SpeedAverageContract.ContractEntry.TABLE_NAME + " (" +
                    ContractEntry._ID + " INTEGER PRIMARY KEY," +
                    ContractEntry.COLUMN_NAME_AVERAGE + " REAL," +
                    ContractEntry.COLUMN_NAME_SAMPLES + " INTEGER)";

    static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + ContractEntry.TABLE_NAME;

    // To prevent someone from accidentally instantiating the contract class,
    // make the constructor private.
    private SpeedAverageContract() {
    }

    /* Inner class that defines the table contents */
    static class ContractEntry implements BaseColumns {
        static final String TABLE_NAME = "speedaverage";
        static final String COLUMN_NAME_AVERAGE = "average";
        static final String COLUMN_NAME_SAMPLES = "samples";
    }
}
