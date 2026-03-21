/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model.ResponseModel;

import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;

@Data
@ToString
public class ResponsePagination<T> {

    private ArrayList<T> products;
    private int pagesCount;

    public ResponsePagination(ArrayList<T> products, int pagesCount) {
        this.products = products;
        this.pagesCount = pagesCount;
    }



}
