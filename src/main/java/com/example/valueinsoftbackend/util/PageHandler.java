/*
 * Copyright (c) Samir Filifl
 */


package com.example.valueinsoftbackend.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
@ToString
public class PageHandler {

    String orderString;
    int pageNumber;
    int maxPageSizeNumber;



    public String handlePageSqlQuery(){
        return  " order by \""+orderString+"\" offset (("+pageNumber+"-1)*10) limit "+maxPageSizeNumber+" ";

    }
}
