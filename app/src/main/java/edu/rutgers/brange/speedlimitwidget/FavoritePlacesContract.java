package edu.rutgers.brange.speedlimitwidget;

import android.provider.BaseColumns;

class FavoritePlacesContract {
    static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + ContractEntry.TABLE_NAME + " (" +
                    ContractEntry._ID + " INTEGER PRIMARY KEY," +
                    ContractEntry.COLUMN_NAME_NAME + " TEXT," +
                    ContractEntry.COLUMN_NAME_ADDRESS + " TEXT," +
                    ContractEntry.COLUMN_NAME_LATITUDE + " REAL," +
                    ContractEntry.COLUMN_NAME_LONGITUDE + " REAL)";
    static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + ContractEntry.TABLE_NAME;

    // To prevent someone from accidentally instantiating the contract class,
    // make the constructor private.
    private FavoritePlacesContract() {
    }

    /* Inner class that defines the table contents */
    static class ContractEntry implements BaseColumns {
        static final String TABLE_NAME = "favorites";
        static final String COLUMN_NAME_NAME = "name";
        static final String COLUMN_NAME_ADDRESS = "address";
        static final String COLUMN_NAME_LATITUDE = "latitude";
        static final String COLUMN_NAME_LONGITUDE = "longitude";
    }

}

