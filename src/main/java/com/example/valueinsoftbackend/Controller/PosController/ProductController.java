package com.example.valueinsoftbackend.Controller.PosController;


import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosProduct;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.ValueinsoftBackendApplication;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@RestController
@RequestMapping("/products")
@CrossOrigin("*")
public class ProductController {

    @GetMapping
    public Product getgreet() {
        for (int i = 0; i < ValueinsoftBackendApplication.productArrayList.size(); i++) {
            Product p = ValueinsoftBackendApplication.productArrayList.get(i);
            if (p.getProductId() == 111) {
                return p;
            }
        }
        return null;
    }



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
                return DbPosProduct.getProductBySearchCompanyName(text,id);
            case "shortCate":
                System.out.println("shortCate");
                break;
        }


        return null;
    }


    @PostMapping("/{branchId}/saveProduct")
    Product newEmployee(@RequestBody Product newProProduct,@PathVariable String branchId) {
        DbPosProduct.AddProduct(newProProduct,branchId);
        return newProProduct;
    }
}
