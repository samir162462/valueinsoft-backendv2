package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.ArrayList;
import java.util.Map;


@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/Company")

public class CompanyController {



    private final DbCompany DbCompany ;
    private final DbUsers dbUsers ;

    public CompanyController(com.example.valueinsoftbackend.DatabaseRequests.DbCompany dbCompany, DbUsers dbUsers) {
        DbCompany = dbCompany;
        this.dbUsers = dbUsers;
    }

    @RequestMapping(value = "/getCompany", method = RequestMethod.GET)
    @ResponseBody
    public Company getPersonsByNames(


            @RequestParam("id") String id

    ) {

        User u1 = dbUsers.getUser(id);

        return DbCompany.getCompanyByOwnerId(u1.getUserId() + "");
    }


    @RequestMapping(value = "/getAllCompanies", method = RequestMethod.GET)
    @ResponseBody
    public ArrayList<Company> getAllCompanies(
    ) {

        return DbCompany.getAllCompanies();
    }



    //getCompanyAndBranchesByUserName
    @RequestMapping(value = "/getCompanyAndBranchesByUserName", method = RequestMethod.GET)
    @ResponseBody
    public Company CompanyAndBranchesByUserName(


            @RequestParam("id") String id

    ) {


        return DbCompany.getCompanyAndBranchesByUserName(id);
    }

    @RequestMapping(value = "/getCompanyById", method = RequestMethod.GET)
    @ResponseBody
    public Company getCompanyById(


            @RequestParam("id") String id

    ) {


        return DbCompany.getCompanyById(id);
    }

    @PostMapping("/saveCompany")

    public Object newCompany(@RequestBody Map<String, Object> body) {

        String ownerName = body.get("ownerName").toString();
        String companyName = body.get("companyName").toString();
        String branchName = body.get("branchName").toString();
        String plan = body.get("plan").toString();
        String comImg = body.get("comImg").toString();
        String currency = body.get("currency").toString();
        int planPrice = Integer.valueOf(body.get("EstablishPrice").toString()) ;
        Company com = null;
        try {
            String msg = "";
            msg =  DbCompany.AddCompany(companyName, branchName, plan, planPrice, ownerName,comImg,currency);
            if (msg.contains("already")) {
                return msg;
            }
            com = DbCompany.getCompanyByOwnerId(ownerName);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;

        }


        return com;

    }

    @PutMapping("/updateImg/{companyId}")
    public ResponseEntity<String> updateImg(@PathVariable int  companyId, @RequestBody Map<String, String> requestBody) {

        return DbCompany.UpdateCompanyImg(companyId,requestBody.get("imgFile"));
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
