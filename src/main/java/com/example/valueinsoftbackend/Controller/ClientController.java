package com.example.valueinsoftbackend.Controller;


import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbClient;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.Model.Client;
import com.example.valueinsoftbackend.Model.Company;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Map;

@RestController
@RequestMapping("/Client")
@CrossOrigin("*")
public class ClientController {


    @RequestMapping(path = "/{companyId}/getClientByPhone/{phone}/{bid}", method = RequestMethod.GET)
    @ResponseBody
    public Client getClientByPhone(
            @PathVariable("phone") String phone,
            @PathVariable("companyId") int companyId,
            @PathVariable("bid") int bid

    ) {
        //get just the client in the list
        return DbClient.getClientByPhoneNumberOrName(companyId,phone, null, null, null, bid).get(0);
    }

    @RequestMapping(path = "/{companyId}/getLatestClients/{max}/{bid}", method = RequestMethod.GET)
    @ResponseBody
    public ArrayList<Client> getLastClients(
            @PathVariable("max") int max,
            @PathVariable("companyId") int companyId,
            @PathVariable("bid") int bid

    ) {
        //get just the client in the list
        return DbClient.getLatestClients(companyId,max, bid);
    }

    @RequestMapping(path = "/{companyId}/getClientsByName/{name}/{bid}", method = RequestMethod.GET)
    @ResponseBody
    public ArrayList<Client> getClientsByName(
            @PathVariable("name") String name,
            @PathVariable("companyId") int companyId,
            @PathVariable("bid") int bid
    ) {
        //get just the client in the list
        return DbClient.getClientByPhoneNumberOrName(companyId,null, name, null, null, bid);
    }

    @PostMapping("/{companyId}/AddClient")

    public ResponseEntity<Object> newUser(@RequestBody Map<String, Object> body,
                                          @PathVariable("companyId") int companyId) {
        int branchId = -1;
        String message = "";
        String clientName = body.get("clientName").toString();
        String clientPhone = body.get("clientPhone").toString();
        String gender = body.get("gender").toString();
        String description = body.get("desc").toString();
        branchId = (int) body.get("branchId");
        try {

            message = DbClient.AddClient(companyId,clientName, clientPhone, branchId, gender, description);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        String found = message.contains("exist") || message.contains("Taken") ? "true" : "false"; //

        return ResponseEntity.status(HttpStatus.ACCEPTED).body("{\"title\" : \"" + message + "\", \"found\" : \"" + found + "\" }");

    }


}
