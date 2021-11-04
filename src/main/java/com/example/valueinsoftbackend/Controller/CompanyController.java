package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;



import java.util.Map;



@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/Company")

public class CompanyController {


    @RequestMapping(value = "/getCompany", method = RequestMethod.GET )
    @ResponseBody
    public Company getPersonsByNames(


            @RequestParam("id") String id

    ) {

        User u1 =  DbUsers.getUser(id);

        return  DbCompany.getCompanyByOwnerId(u1.getUserId()+"");
    }




    @RequestMapping(value = "/getCompanyById", method = RequestMethod.GET)
    @ResponseBody
    public Company getCompanyById(


            @RequestParam("id") String id

    ) {


        return  DbCompany.getCompanyById( id);
    }

    @PostMapping("/saveCompany")

    public Company newCompany(@RequestBody Map<String,Object> body) {

        String ownerName = body.get("ownerName").toString();
        String companyName = body.get("companyName").toString();
        String branchName = body.get("branchName").toString();
        String plan = body.get("plan").toString();
        int planPrice = (int) body.get("EstablishPrice");
        Company com = null;
        try {
            DbCompany.AddCompany(companyName,branchName,plan,planPrice,ownerName);
            com = DbCompany.getCompanyByOwnerId(ownerName);
        }catch (Exception e )
        {
            System.out.println(e.getMessage());
            return null;

        }



        return com;

    }
    @GetMapping("/listHeaders")
    public ResponseEntity<String> listAllHeaders(
            @RequestHeader Map<String, String> headers) {
        headers.forEach((key, value) -> {
            System.out.println(String.format("Header '%s' = %s", key, value));
        });
        return new ResponseEntity<String>(
                String.format("Listed %d headers", headers.size()), HttpStatus.OK);
    }
}
