package com.example.valueinsoftbackend.Model;

import java.util.ArrayList;

public class Category {
    int id;
    String name;
    int branchId;
    ArrayList<SubCategory> subCategories;

    public Category(int id, String name, int branchId, ArrayList<SubCategory> subCategories) {
        this.id = id;
        this.name = name;
        this.branchId = branchId;
        this.subCategories = subCategories;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public ArrayList<SubCategory> getSubCategories() {
        return subCategories;
    }

    public void setSubCategories(ArrayList<SubCategory> subCategories) {
        this.subCategories = subCategories;
    }

    @Override
    public String toString() {
        return "Category{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", branchId=" + branchId +
                ", subCategories=" + subCategories +
                '}';
    }
}
