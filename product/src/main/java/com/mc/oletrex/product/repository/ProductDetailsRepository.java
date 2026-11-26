package com.mc.oletrex.product.repository;

import com.mc.oletrex.product.model.ProductDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** ProductDetails shares the product's id, so findById(product.getId()) loads a product's details. */
public interface ProductDetailsRepository extends JpaRepository<ProductDetails, Long> {

    /*
     * Loading a page of details takes two queries, one per collection. Fetching both collections
     * in a single query would multiply rows (attributes x images per product). Run both in the
     * same transaction: the second query fills the images of the entities the first one returned.
     */

    @Query("select d from ProductDetails d left join fetch d.attributes where d.id in :ids")
    List<ProductDetails> findAllWithAttributesByIdIn(@Param("ids") Collection<Long> ids);

    @Query("select d from ProductDetails d left join fetch d.images where d.id in :ids")
    List<ProductDetails> findAllWithImagesByIdIn(@Param("ids") Collection<Long> ids);
}
