package com.example.valueinsoftbackend.util;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ConvertStringToTimeStamp
{
    public static Timestamp convertString(String timeString)
    {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS");
            Date parsedDate = dateFormat.parse(timeString);
            Timestamp timestamp = new java.sql.Timestamp(parsedDate.getTime());
            return timestamp;
        } catch(Exception e){
            return null;
        }
    }
}
