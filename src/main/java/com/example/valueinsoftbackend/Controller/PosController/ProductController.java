package com.example.valueinsoftbackend.Controller.PosController;


import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosProduct;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.ValueinsoftBackendApplication;
import com.google.gson.JsonObject;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@RestController
@RequestMapping("/products")
@CrossOrigin("*")
public class ProductController {





    @RequestMapping(path = "/search/{searchType}/{id}/{text}", method = RequestMethod.GET)
    public ArrayList<Product> getProducts(@PathVariable String id, @PathVariable String searchType, @PathVariable String text) {
        // code here
        switch (searchType) {
            case "dir":
                System.out.println("dir");
                System.out.println("id "+id);
                System.out.println("text prams : "+text);
                String[] words = text.split("\\s+");
                for (int i = 0; i < words.length; i++) {
                    // You may want to check for a non-word character before blindly
                    // performing a replacement
                    // It may also be necessary to adjust the character class
                   // words[i] = words[i].replaceAll("[^\\w]", "");

                }

                return DbPosProduct.getProductBySearchText(words,id);
            case "comName":
                System.out.println("comName");
                return DbPosProduct.getProductBySearchCompanyName(text.trim(),id);
            case "shortCate":
                System.out.println("shortCate");
                break;
            case "allData":
                System.out.println("allData");
                break;
        }


        return null;
    }


    @PostMapping("/{branchId}/saveProduct")
    String newProduct(@RequestBody Product newProProduct, @PathVariable String branchId) {
        return DbPosProduct.AddProduct(newProProduct,branchId).toString();
    }

    //--------------editProduct------------------
    @PutMapping("/{branchId}/editProduct")
    String EditProduct(@RequestBody Product editProduct, @PathVariable String branchId) {
        return DbPosProduct.EditProduct(editProduct,branchId).toString();
    }
}
