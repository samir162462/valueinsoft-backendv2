/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model.ResponseModel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;

@Data
@AllArgsConstructor
@ToString
public class ResponsePagination<e> {

     ArrayList<e> products;
    int pagesCount ;



}
