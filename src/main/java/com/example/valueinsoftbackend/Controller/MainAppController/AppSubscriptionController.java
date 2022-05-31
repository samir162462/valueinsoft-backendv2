/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.MainAppController;


import com.example.valueinsoftbackend.DatabaseRequests.DbApp.DbSubscription;
import com.example.valueinsoftbackend.Model.AppModel.AppModelSubscription;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Map;

@RestController
@RequestMapping("/appSubscription")
@CrossOrigin("*")
public class AppSubscriptionController  {
    @RequestMapping(value = "/{branchId}",method = RequestMethod.GET)
    public ArrayList<AppModelSubscription> AppModelSubscriptionByBranchId(@PathVariable int branchId  ) throws Exception
    {


        return DbSubscription.getBranchSubscription(branchId);

    }

    @RequestMapping(value = "/AddSubscription",method = RequestMethod.POST)
    public String AddSubscription(@RequestBody AppModelSubscription appModelSubscription ) throws Exception
    {


        return DbSubscription.AddBranchSubscription(appModelSubscription);

    }

    @GetMapping(path = {"/Res"})
    public Map<String, String> PayMobTransactionCallBack(
                     @RequestParam(required=false) Map<String,String> qparams) {
        qparams.forEach((a,b) -> {
            System.out.println(String.format("%s -> %s",a,b));
            System.out.println(qparams.get("success"));
        });
        return qparams;

    }

}
