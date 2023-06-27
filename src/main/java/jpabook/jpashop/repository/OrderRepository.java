package jpabook.jpashop.repository;

import jpabook.jpashop.domain.Order;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderRepository {
    private final EntityManager em;

    public void save(Order order) {
        em.persist(order);
    }

    public Order findOne(Long id) {
        return em.find(Order.class, id);
    }

    public List<Order> findAllByString(OrderSearch orderSearch) {
//        em.createQuery("select o from Order o join o.member m" +
//                " where o.status = :status" +
//                " and m.name like :name", Order.class)
//                .setParameter("status", orderSearch.getOrderStatus())
//                .setParameter("name", orderSearch.getMemberName())
//                // .setFirstPosition(100) -> 페이징 할때 유용 100부터 1000개 가져오는..
//                .setMaxResults(1000)
//                .getResultList();
        //language=JPQL
        String jpql = "select o From Order o join o.member m";
        boolean isFirstCondition = true;

        //주문 상태 검색
        if (orderSearch.getOrderStatus() != null) {
            if (isFirstCondition) {
                jpql += " where";
                isFirstCondition = false;
            } else {
                jpql += " and";
            }
            jpql += " o.status = :status";
        }

        //회원 이름 검색
        if (StringUtils.hasText(orderSearch.getMemberName())) {
            if (isFirstCondition) {
                jpql += " where";
                isFirstCondition = false;
            } else {
                jpql += " and";
            }
            jpql += " m.name like :name";
        }

        TypedQuery<Order> query = em.createQuery(jpql, Order.class)
                .setMaxResults(1000); //최대 1000건

        if (orderSearch.getOrderStatus() != null) {
            query = query.setParameter("status", orderSearch.getOrderStatus());
        }

        if (StringUtils.hasText(orderSearch.getMemberName())) {
            query = query.setParameter("name", orderSearch.getMemberName());
        }

        return query.getResultList();
    }


    /**
     * JPA Criteria
     *
     * @param //orderSearch
     * @return
     */
//    public List<Order> findAllByCriteria(OrderSearch orderSearch) {
//
//    }

    /**
     * fetch join 전략: 쿼리 한번으로 최적화
     * 실제 sql문에서는 join한 m.*, d.* 도 함께 select 된다.
     */
    public List<Order> findAllWithMemberRepository() {
        return em.createQuery(
                "select o from Order o" +
                " join fetch o.member m" +
                " join fetch o.delivery d", Order.class)
        .getResultList();
    }


    // 약간 비추: fetch 조인이 중요! 한방 쿼리
    /**
     * distinct 를 사용한 이유
     * - 1대다 조인이 있으므로 데이터베이스 row가 증가한다.
     * - 그 결과 같은 order 엔티티의 조회 수도 증가하게 된다.
     * - JPA의 distinct는 SQL에 distinct를 추가하고, 더해서 같은 엔티티가 조회되면, 애플리케이션에서 중복을 걸러준다.
     * - 이 예에서 order가 컬렉션 페치 조인 때문에 중복 조회 되는 것을 막아준다.
     * 단점: 페이징 불가능
     * 컬렉션 페치 조인은 1개만 사용할 수 있다. 컬렉션 둘 이상에 페치 조인을 사용하면 안된다. 데이터가 부정합하게 조회될 수 있다
     */
   public List<Order> findAllWithItem() {
        return em.createQuery("select distinct o from Order o" + // distinct 붙임
                " join fetch o.member m" +
                " join fetch o.delivery d" +
                " join fetch o.orderItems io" + // orders->order_item 이 일대다 관계 -> 중복 데이터가 발생
                " join fetch io.item i", Order.class).getResultList();
    }


    // 추천
    /**
     * 아래는 일대다 관계(orderItems)에서 fetch join 을 적용하지 않음으로서, 페이징이 가능해짐
     * 일대다 관계가 없기 때문에 중복도 발생하지 않아, distinct 를 넣어야할 이유도 없어지는것.
     */
    public List<Order> findAllWithMemberDelivery(int offset, int limit) {
        return em.createQuery(
                "select o from Order o" +
                        " join fetch o.member m" +
                        " join fetch o.delivery d", Order.class)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }
}
