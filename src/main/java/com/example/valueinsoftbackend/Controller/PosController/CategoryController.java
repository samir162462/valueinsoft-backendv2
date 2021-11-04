package com.example.valueinsoftbackend.Controller.PosController;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosCategory;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosProduct;
import com.example.valueinsoftbackend.Model.Category;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.SubCategory;
import com.example.valueinsoftbackend.util.CustomPair;
import org.springframework.web.bind.annotation.*;

import java.util.*;


@RestController
@RequestMapping("/Categories")
@CrossOrigin("*")
public class CategoryController {


    @PostMapping("/{branchId}/saveProduct")
    Map<String, Object> newEmployee(@RequestBody Map<String, Object> body, @PathVariable String branchId) {

        //Delete
        DbPosCategory.DeleteCategoryByBranchId(Integer.valueOf(branchId));

        // DbPosProduct.AddProduct(newProProduct,branchId);
        ArrayList<Category> cateList = new ArrayList<>();
        System.out.println(body);
        for (String key : body.keySet()) {
            Object values = body.get(key);
            System.out.println(key);
            ArrayList<String>strList = new ArrayList<>();
            ArrayList<SubCategory>subCategories = new ArrayList<>();
            for (int i = 0; i < Arrays.asList(values).size(); i++) {
                System.out.println(Arrays.asList(values).get(i).getClass());
                strList.add(Arrays.asList(values).get(i).toString());
                SubCategory sc = new SubCategory(1,strList,1);
                subCategories.add(sc);
            }
              Category cate = new Category(1,key,Integer.valueOf(branchId),subCategories);
            System.out.println(cate.toString());
            cateList.add(cate);
        }
        DbPosCategory.AddCategory(cateList);
        DbPosCategory.AddSubCategory(cateList);

        return body;
    }

    @RequestMapping(path = "/getCategories/{branchId}", method = RequestMethod.GET)
    public ArrayList<CustomPair> getCategories(@PathVariable int branchId) {

        // code here
        ArrayList<CustomPair>customPairs = new ArrayList<>();

        ArrayList<Category> categoryArrayList= DbPosCategory.getCategoriesByBranchId(branchId);
        for (int i = 0; i < categoryArrayList.size(); i++) {
            Category cate = categoryArrayList.get(i);
            System.out.println(DbPosCategory.getSubCategoriesByCategoryId(cate.getId()));
            CustomPair customPair = new CustomPair(cate.getName(),DbPosCategory.getSubCategoriesByCategoryId(cate.getId()).getNames());
            customPairs.add(customPair);
        }

        return customPairs;

    }


}
