package com.example.valueinsoftbackend.Controller.PosController;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosCategory;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosProduct;
import com.example.valueinsoftbackend.Model.Category;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.SubCategory;
import com.example.valueinsoftbackend.util.CustomPair;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.postgresql.util.PGobject;
import org.springframework.web.bind.annotation.*;

import java.sql.Array;
import java.util.*;


@RestController
@RequestMapping("/Categories")
@CrossOrigin("*")
public class CategoryController {




    @PostMapping("/{branchId}/saveCategory")

    String  newCategory(@RequestBody String payload, @PathVariable int branchId) {

        DbPosCategory.AddCategoryJson(branchId,payload);//getCategoryJson



        return payload;
    }
    @RequestMapping(path = "/getCategoryJson/{branchId}", method = RequestMethod.GET)
    public ArrayList<CustomPair> getCategoriesJson(@PathVariable int branchId) {

        try {

            ArrayList<CustomPair>customPairs = new ArrayList<>();

            // CustomPair customPair= new CustomPair()
            Map<String, Object> response = new ObjectMapper().readValue(DbPosCategory.getCategoryJson(branchId), HashMap.class);
            for (int i = 0; i < response.size(); i++) {
                ArrayList<String> list1 = new ArrayList<String>();
                String[] Data = response.values().toArray()[i].toString().trim().replace("[","").replace("]","").split(",");
                for (int j = 0; j < Data.length; j++) {
                    list1.add(Data[j]);
                }
                CustomPair customPair  = new CustomPair(response.keySet().toArray()[i].toString(),list1 );
                customPairs.add(customPair);
                System.out.println(response.keySet().toArray()[i]);
                System.out.println(Data);
            }

            return customPairs;

        }catch (Exception e)
        {
            System.out.println(e);
        }


        return null;

    }





}
