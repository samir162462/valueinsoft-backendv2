package com.example.valueinsoftbackend.Controller.PosController;


import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosProduct;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.ProductFilter;
import com.example.valueinsoftbackend.ValueinsoftBackendApplication;
import com.google.gson.JsonObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@RestController
@RequestMapping("/products")
@CrossOrigin("*")
public class ProductController {


    @RequestMapping(path = "/search/{searchType}/{companyId}/{branchId}/{text}", method = RequestMethod.GET)
    public ArrayList<Product> getProducts(@PathVariable int companyId,
                                          @PathVariable String branchId,
                                          @PathVariable String searchType,
                                          @PathVariable String text) {
        // code here
        switch (searchType) {
            case "dir":
                System.out.println("dir -> " + companyId);
                System.out.println("text prams : " + text);
                String[] words = text.split("\\s+");
                for (int i = 0; i < words.length; i++) {
                    // You may want to check for a non-word character before blindly
                    // performing a replacement
                    // It may also be necessary to adjust the character class
                    // words[i] = words[i].replaceAll("[^\\w]", "");
                }

                return DbPosProduct.getProductBySearchText(words, branchId, companyId,null);
            case "comName":
                System.out.println("comName");
                return DbPosProduct.getProductBySearchCompanyName(text.trim(), branchId, companyId ,null);
            case "Barcode":
                return DbPosProduct.getProductBySearchBarcode(text.trim(), branchId, companyId ,null);
            case "allData":
                System.out.println("allData");
                break;
        }


        return null;
    }
    //todo -------- filter Search POST DAta
    @RequestMapping(path = "/search/{searchType}/{companyId}/{branchId}/{text}/filter", method = RequestMethod.POST)
    public ArrayList<Product> getProductsBySearchFilter(@PathVariable int companyId,
                                          @PathVariable String branchId,
                                          @PathVariable String searchType,
                                          @PathVariable String text,
                                            @RequestBody ProductFilter productFilter)
        {
        // code here
            System.out.println(productFilter.toString());
        switch (searchType) {
            case "dir":
                System.out.println("dir -> " + companyId);
                System.out.println("text prams : " + text);
                String[] words = text.split("\\s+");
                for (int i = 0; i < words.length; i++) {
                    // You may want to check for a non-word character before blindly
                    // performing a replacement
                    // It may also be necessary to adjust the character class
                    // words[i] = words[i].replaceAll("[^\\w]", "");
                }

                return DbPosProduct.getProductBySearchText(words, branchId, companyId,productFilter);
            case "comName":
                System.out.println("comName");
                return DbPosProduct.getProductBySearchCompanyName(text.trim(), branchId, companyId,productFilter);
            case "shortCate":
                System.out.println("shortCate");
                break;
            case "allData":
                System.out.println("allData");
                return DbPosProduct.getProductsAllRange(branchId, companyId,productFilter);

        }


        return null;
    }


    //----get----

    @GetMapping("{companyId}/{branchId}/{productId}")
    Product productById( @PathVariable int branchId, @PathVariable int companyId , @PathVariable int productId) {
        return DbPosProduct.getProductById(productId, branchId, companyId);
    }

    @PostMapping("{companyId}/{branchId}/saveProduct")
    ResponseEntity<Object> newProduct(@RequestBody Product newProProduct, @PathVariable String branchId, @PathVariable int companyId) {
        return DbPosProduct.AddProduct(newProProduct, branchId, companyId);
    }

    //--------------editProduct------------------
    @PutMapping("{companyId}/{branchId}/editProduct")
    ResponseEntity<Object> EditProduct(@RequestBody Product editProduct, @PathVariable String branchId, @PathVariable int companyId) {
        return DbPosProduct.EditProduct(editProduct, branchId, companyId);
    }
    //--Search ProdsNames
    @GetMapping("/PN/{companyId}/{branchId}/{text}")
    ResponseEntity<Object> productNames( @PathVariable int branchId, @PathVariable int companyId , @PathVariable String text) {
        return DbPosProduct.getProductNames(text, branchId, companyId);
    }


}
