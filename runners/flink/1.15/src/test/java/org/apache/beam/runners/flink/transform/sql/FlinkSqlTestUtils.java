/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.runners.flink.transform.sql;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Objects;
import org.apache.beam.runners.flink.translation.types.TypeInformationCoder;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.options.ValueProvider;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.values.PCollection;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.SimpleTypeSerializerSnapshot;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.TypeSerializerSnapshot;
import org.apache.flink.api.common.typeutils.base.TypeSerializerSingleton;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonFormat;

/** Unit tests for {@link FlinkSql}. */
public class FlinkSqlTestUtils {
  public static final Order ORDER =
      new Order(1000L, "Mango", 10, 1.9, "Jean Doe", new Timestamp(1000L));
  public static final NonPojoProduct NON_POJO_PRODUCT = new NonPojoProduct("Mango", "Thailand");

  public static final String ORDERS_DDL =
      String.format(
          "CREATE TABLE Orders (\n"
              + "    orderNumber  BIGINT,\n"
              + "    product      String,\n"
              + "    amount       INT,\n"
              + "    price        DECIMAL(8, 2),\n"
              + "    buyer        STRING,\n"
              + "    orderTime    TIMESTAMP(3)\n"
              + ") WITH (\n"
              + "  'connector' = 'filesystem',\n"
              + "  'path' = '%s',\n"
              + "  'format' = 'csv',\n"
              + "  'csv.allow-comments' = 'true'\n"
              + ")",
          getFilePath("Orders"));

  public static final String PRODUCTS_DDL =
      String.format(
          "CREATE TABLE Products (\n"
              + "    name          String,\n"
              + "    country       String\n"
              + ") WITH (\n"
              + "  'connector' = 'filesystem',\n"
              + "  'path' = '%s',\n"
              + "  'format' = 'csv',\n"
              + "  'csv.allow-comments' = 'true'\n"
              + ")",
          getFilePath("Products"));

  public static final String SALES_BY_MANUFACTURE_DDL =
      "CREATE TABLE SalesByManufacture (\n"
          + "    manufactureID     INT,\n"
          + "    sales             DECIMAL(32, 2)\n"
          + ") WITH (\n"
          + "  'connector' = 'print'\n"
          + ")";

  // ------------- private helper methods -------------------
  public static String getFilePath(String fileName) {
    return new File(
            FlinkSqlTestUtils.class.getClassLoader().getResource("tables/" + fileName).getFile())
        .getAbsolutePath();
  }

  public static PCollection<Order> getSingletonOrderPCollection(String name, Pipeline pipeline) {
    return getSingletonPCollection(name, pipeline, ORDER, TypeInformation.of(Order.class));
  }

  public static <T> PCollection<T> getSingletonPCollection(
      String name, Pipeline pipeline, T element, TypeInformation<T> typeInfo) {
    return pipeline.apply(
        name,
        Create.ofProvider(
            new ValueProvider<T>() {
              boolean returned;

              @Override
              public T get() {
                returned = true;
                return element;
              }

              @Override
              public boolean isAccessible() {
                return !returned;
              }
            },
            new TypeInformationCoder<T>(typeInfo)));
  }

  // -------------------- public pojo classes ------------------------

  @JsonPropertyOrder({"orderNumber", "product", "amount", "price", "buyer", "orderTime"})
  public static final class Order implements Serializable {
    public long orderNumber;
    public String product;
    public int amount;
    public double price;
    public String buyer;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    public Timestamp orderTime;

    public Order() {}

    public Order(
        long orderNumber,
        String product,
        int amount,
        double price,
        String buyer,
        Timestamp orderTime) {
      this.orderNumber = orderNumber;
      this.product = product;
      this.amount = amount;
      this.price = price;
      this.buyer = buyer;
      this.orderTime = orderTime;
    }

    @Override
    public String toString() {
      return String.format(
          "orderNumber=%d, product=%s, amount=%d, price=%f, buyer=%s, orderTime=%s",
          orderNumber, product, amount, price, buyer, orderTime);
    }

    @Override
    public int hashCode() {
      // Ignore order time due to timezone issue.
      return Objects.hash(orderNumber, product, amount, price, buyer);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Order)) {
        return false;
      }
      Order other = (Order) obj;
      // Ignore order time due to timezone issue.
      return other.orderNumber == orderNumber
          && other.price == price
          && other.amount == amount
          && other.buyer.equals(buyer)
          && other.product.equals(product);
    }
  }

  public static final class NonPojoProduct {

    private final String name;
    private final String country;

    public NonPojoProduct(String name, String country) {
      this.name = name;
      this.country = country;
    }

    public String getName() {
      return name;
    }

    public String getCountry() {
      return country;
    }

    public static TypeInformation<NonPojoProduct> getTypeInfo() {
      return NonPojoProductTypeInfo.INSTANCE;
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, country);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof NonPojoProduct)) {
        return false;
      }
      NonPojoProduct other = (NonPojoProduct) obj;
      return other.name.equals(name) && other.country.equals(country);
    }

    @Override
    public String toString() {
      return String.format("name: %s, country = %s", name, country);
    }
  }

  public static final class NonPojoProductTypeInfo extends TypeInformation<NonPojoProduct> {
    private static final NonPojoProductTypeInfo INSTANCE = new NonPojoProductTypeInfo();

    @Override
    public boolean isBasicType() {
      return false;
    }

    @Override
    public boolean isTupleType() {
      return false;
    }

    @Override
    public int getArity() {
      return 2;
    }

    @Override
    public int getTotalFields() {
      return 2;
    }

    @Override
    public Class<NonPojoProduct> getTypeClass() {
      return NonPojoProduct.class;
    }

    @Override
    public boolean isKeyType() {
      return true;
    }

    @Override
    public TypeSerializer<NonPojoProduct> createSerializer(ExecutionConfig config) {
      return NonPOjoProductSerializer.INSTANCE;
    }

    @Override
    public String toString() {
      return "NonPojoProductTypeInformation";
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof NonPojoProductTypeInfo;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public boolean canEqual(Object obj) {
      return obj instanceof NonPojoProductTypeInfo;
    }
  }

  public static final class NonPOjoProductSerializer
      extends TypeSerializerSingleton<NonPojoProduct> {
    private static final NonPOjoProductSerializer INSTANCE = new NonPOjoProductSerializer();

    @Override
    public boolean isImmutableType() {
      return true;
    }

    @Override
    public NonPojoProduct createInstance() {
      return new NonPojoProduct("", "");
    }

    @Override
    public NonPojoProduct copy(NonPojoProduct from) {
      return from;
    }

    @Override
    public NonPojoProduct copy(NonPojoProduct from, NonPojoProduct reuse) {
      return copy(from);
    }

    @Override
    public int getLength() {
      return -1;
    }

    @Override
    public void serialize(NonPojoProduct record, DataOutputView target) throws IOException {
      target.writeInt(record.name.length());
      target.writeBytes(record.name);
      target.writeInt(record.country.length());
      target.writeBytes(record.country);
    }

    @Override
    public NonPojoProduct deserialize(DataInputView source) throws IOException {
      int size = source.readInt();
      byte[] nameBytes = new byte[size];
      source.read(nameBytes);
      size = source.readInt();
      byte[] countryBytes = new byte[size];
      source.read(countryBytes);
      return new NonPojoProduct(
          new String(nameBytes, StandardCharsets.UTF_8),
          new String(countryBytes, StandardCharsets.UTF_8));
    }

    @Override
    public NonPojoProduct deserialize(NonPojoProduct reuse, DataInputView source)
        throws IOException {
      return deserialize(source);
    }

    @Override
    public void copy(DataInputView source, DataOutputView target) throws IOException {
      serialize(deserialize(source), target);
    }

    @Override
    public TypeSerializerSnapshot<NonPojoProduct> snapshotConfiguration() {
      return new NonPojoProductSerializerSnapshot();
    }

    public static class NonPojoProductSerializerSnapshot
        extends SimpleTypeSerializerSnapshot<NonPojoProduct> {
      public NonPojoProductSerializerSnapshot() {
        super(() -> INSTANCE);
      }
    }
  }

  public static final class CountryAndSales implements Serializable {
    public String country;
    public double sales;

    @Override
    public String toString() {
      return String.format("{%s, %f}", country, sales);
    }

    @Override
    public int hashCode() {
      return Objects.hash(country, sales);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof CountryAndSales)) {
        return false;
      }
      CountryAndSales other = (CountryAndSales) obj;
      return other.country.equals(country) && other.sales == sales;
    }
  }

  public static final class ProductAndSales implements Serializable {
    public String product;
    public double sales;

    @Override
    public String toString() {
      return String.format("{%s, %f}", product, sales);
    }

    @Override
    public int hashCode() {
      return Objects.hash(product, sales);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ProductAndSales)) {
        return false;
      }
      ProductAndSales other = (ProductAndSales) obj;
      return other.product.equals(product) && other.sales == sales;
    }
  }
}
