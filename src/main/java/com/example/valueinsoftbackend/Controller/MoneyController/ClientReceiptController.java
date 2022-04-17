/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.MoneyController;

import com.example.valueinsoftbackend.DatabaseRequests.DbMoney.DBMClientReceipt;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvSales;
import com.example.valueinsoftbackend.Model.Sales.ClientReceipt;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Map;

@RestController
@RequestMapping("/CR")
@CrossOrigin("*")
public class ClientReceiptController {



    @RequestMapping(value = "/{companyId}/{clientId}",method = RequestMethod.GET)
    public ArrayList<ClientReceipt> clientReceipts(@PathVariable int companyId,@PathVariable int clientId  ) throws Exception
    {

        System.out.println(clientId);
        return DBMClientReceipt.getClientReceipts(companyId,clientId);

    }
    @RequestMapping(value = "/{companyId}/{branchId}/{startTime}/{endTime}",method = RequestMethod.GET)
    public ArrayList<ClientReceipt> clientReceiptsByTime(@PathVariable int companyId,@PathVariable int branchId,@PathVariable String startTime,@PathVariable String endTime  ) throws Exception
    {
        System.out.println("startTime: "+startTime);
        System.out.println("endTime: "+endTime);

        return DBMClientReceipt.getClientReceiptsByTime(companyId,branchId,startTime,endTime);

    }

    @RequestMapping(value = "/{companyId}",method = RequestMethod.POST)
    public String AddClientReceipts(@RequestBody ClientReceipt clientReceipt ,@PathVariable int companyId  ) throws Exception
    {


        return DBMClientReceipt.AddClientReceipt(companyId,clientReceipt);

    }

}
