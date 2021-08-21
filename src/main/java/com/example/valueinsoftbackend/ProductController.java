package com.example.valueinsoftbackend;


import Model.Product;
import com.example.valueinsoftbackend.User;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@RestController
@RequestMapping("/products")
@CrossOrigin("*")
public class ProductController {

    @GetMapping
    public Product getgreet()
    {
        for (int i = 0; i < ValueinsoftBackendApplication.productArrayList.size(); i++) {
            Product p = ValueinsoftBackendApplication.productArrayList.get(i);
            if (p.getProductId() == 111)
            {
                return p;
            }
        }
        return null;
    }
    @RequestMapping(value = "/prodName", method = RequestMethod.GET)
    @ResponseBody
    public HashSet<Product> getProductsByNames(



            @RequestParam("id") List<String> id

    ) {
        HashSet<Product> productArrayList = new HashSet<>();
        System.out.println(id.size());
        for (int i = 0; i < ValueinsoftBackendApplication.productArrayList.size(); i++) {
            Product p = ValueinsoftBackendApplication.productArrayList.get(i);

            for (int j = 0; j < id.size(); j++) {
                if (p.getProductName().contains(id.get(j)))
                {
                    productArrayList.add(p);

                }
            }

        }
        System.out.println(productArrayList.iterator().next().getQuantity());
        return productArrayList;
    }

}
