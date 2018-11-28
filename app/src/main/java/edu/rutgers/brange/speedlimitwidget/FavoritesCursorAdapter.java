package edu.rutgers.brange.speedlimitwidget;

import android.content.Context;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static android.provider.BaseColumns._ID;

public class FavoritesCursorAdapter extends CursorAdapter {
    public FavoritesCursorAdapter(Context context, Cursor cursor) {
        super(context, cursor, 0);
    }

    // The newView method is used to inflate a new view and return it,
    // you don't bind any data to the view at this point.
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return LayoutInflater.from(context).inflate(R.layout.favorite_place_item, parent, false);
    }

    // The bindView method is used to bind all data to a given view
    // such as setting the text on a TextView.
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        EditText customNameView = view.findViewById(R.id.custom_name);
        TextView addressView = view.findViewById(R.id.address);

        //int id = Integer.parseInt(cursor.getString(cursor.getColumnIndexOrThrow(_ID)));
        String customNameString = cursor.getString(cursor.getColumnIndexOrThrow(FavoritePlacesContract.ContractEntry.COLUMN_NAME_NAME));
        String addressString = cursor.getString(cursor.getColumnIndexOrThrow(FavoritePlacesContract.ContractEntry.COLUMN_NAME_ADDRESS));
        double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(FavoritePlacesContract.ContractEntry.COLUMN_NAME_LATITUDE));
        double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(FavoritePlacesContract.ContractEntry.COLUMN_NAME_LONGITUDE));

        customNameView.setText(customNameString);

        if (addressString.equals("")) {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            try {
                List<Address> addresses = geocoder.getFromLocation(
                        latitude,
                        longitude,
                        // In this sample, get just a single address.
                        1);
                if (addresses.size() > 0) {
                    Address address = addresses.get(0);
                    addressView.setText(address.getAddressLine(0));
                }
            } catch (IOException ioException) {
            } catch (IllegalArgumentException illegalArgumentException) {
            }

        } else {
            addressView.setText(addressString);
        }

    }
}
