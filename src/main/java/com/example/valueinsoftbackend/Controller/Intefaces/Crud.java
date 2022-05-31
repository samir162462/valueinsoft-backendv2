/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.Intefaces;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

public interface Crud {

    @RequestMapping(value = "{companyId}/{branchId}/{option}/getAll",method = RequestMethod.GET)
    ResponseEntity<Object> getAll(@PathVariable int companyId,@PathVariable int branchId, @PathVariable String option);

    @RequestMapping(value = "{companyId}/{branchId}/getById/{oId}",method = RequestMethod.GET)
    ResponseEntity<Object> getById(@PathVariable int companyId,@PathVariable int branchId,@PathVariable int oId);

    @RequestMapping(value = "{companyId}/{branchId}/create",method = RequestMethod.POST)
    ResponseEntity<Object> create(@RequestBody Object body , @PathVariable int companyId,@PathVariable int branchId);

    @RequestMapping(value = "{companyId}/{branchId}/update",method = RequestMethod.PUT)
    ResponseEntity<Object> updateById(@RequestBody Object body , @PathVariable int companyId,@PathVariable int branchId);

    @RequestMapping(value = "{companyId}/{branchId}/delete",method = RequestMethod.DELETE)
    ResponseEntity<Object> DeleteById(@PathVariable int companyId,@PathVariable int branchId);



}
