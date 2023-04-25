package com.example.valueinsoftbackend.Controller.posController;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosCategory;
import com.example.valueinsoftbackend.Model.MainMajor;
import com.example.valueinsoftbackend.util.CustomPair;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;


@RestController
@RequestMapping("/Categories")
@CrossOrigin("*")
public class CategoryController {


    @PostMapping("/{companyId}/{branchId}/saveCategory")
    ResponseEntity<String> newCategory(@RequestBody String payload, @PathVariable int branchId, @PathVariable int companyId) {

        return DbPosCategory.AddCategoryJson(branchId, payload, companyId);//getCategoryJson


    }

    @RequestMapping(path = "/getCategoryJson/{companyId}/{branchId}", method = RequestMethod.GET)
    public ArrayList<CustomPair> getCategoriesJson(@PathVariable int branchId, @PathVariable int companyId) {
        ArrayList<CustomPair> customPairs = new ArrayList<>();

        try { //For New Code
            String resCate = DbPosCategory.getCategoryJson(branchId, companyId) ;

            if (resCate.startsWith('['+"")) {
                String replaceAllOut = resCate.substring(1, resCate.length() - 2).
                        //     trim().replace("[", "").replace("]", "").
                                replace("}", "").
                        replace("{", "");
                System.out.println("--> "+"inCategories   " +
                        "{"+ replaceAllOut+"}");

                // CustomPair customPair= new CustomPair()
                Map<String, Object> response = new ObjectMapper().readValue("{"+ replaceAllOut+"}"

                        , HashMap.class);
                System.out.println("R--> "+response);

                for (int i = 0; i < response.size(); i++) {
                    ArrayList<String> list1 = new ArrayList<String>();
                    String[] Data = response.values().toArray()[i].toString().trim().replace("[", "").replace("]", "").split(",");
                    for (int j = 0; j < Data.length; j++) {
                        list1.add(Data[j]);
                    }
                    CustomPair customPair = new CustomPair(response.keySet().toArray()[i].toString(), list1);
                    customPairs.add(customPair);
                    System.out.println(response.keySet().toArray()[i]);
                    System.out.println(Data);
                }

            }else{ //For Old Code
                Map<String, Object> response = new ObjectMapper().readValue(DbPosCategory.getCategoryJson(branchId, companyId), HashMap.class);
                for (int i = 0; i < response.size(); i++) {
                    ArrayList<String> list1 = new ArrayList<String>();
                    String[] Data = response.values().toArray()[i].toString().trim().replace("[", "").replace("]", "").split(",");
                    for (int j = 0; j < Data.length; j++) {
                        list1.add(Data[j]);
                    }
                    CustomPair customPair = new CustomPair(response.keySet().toArray()[i].toString(), list1);
                    customPairs.add(customPair);
                    System.out.println(response.keySet().toArray()[i]);
                    System.out.println(Data);
                }
            }

            // CustomPair customPair= new CustomPair()


            return customPairs;

        } catch (Exception e) {
            System.out.println(e);
        }


        return null;

    }


    @RequestMapping(path = "/getCategoryJsonFlat/{companyId}/{branchId}", method = RequestMethod.GET)
    public String getCategoriesJsonObjectsString(@PathVariable int branchId, @PathVariable int companyId) {
        try {
           return DbPosCategory.getCategoryJson(branchId, companyId);
        } catch (Exception e) {
            System.out.println(e);
        }


        return null;

    }

    //Todo ---------- Majors --------------

    @RequestMapping(path = "/getMainMajors/{companyId}", method = RequestMethod.GET)
    public ArrayList<MainMajor> getMainCategories(@PathVariable int companyId) {

        try {
           return DbPosCategory.getMainMajors(companyId);
        } catch (Exception e) {
            System.out.println(e);
        }


        return null;

    }


}
