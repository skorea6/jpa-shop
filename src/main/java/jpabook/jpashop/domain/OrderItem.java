package jpabook.jpashop.domain;

import jpabook.jpashop.domain.item.Item;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 다른 곳에서 new OrderItem 형식으로 불러오지 못하게 막는다. createOrderItem으로만 호출할 수 있게끔. 공동 개발시 필수
public class OrderItem {
    @Id @GeneratedValue
    @Column(name = "order_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item; //주문 상품

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order; // 주문

    private int orderPrice; // 주문 가격
    private int count; // 주문 수량

    // -- 생성 메서드 -- //
    public static OrderItem createOrderItem(Item item, int orderPrice, int count) {
        OrderItem orderItem = new OrderItem();
        orderItem.setItem(item);
        orderItem.setOrderPrice(orderPrice);
        orderItem.setCount(count);

        item.removeStock(count);
        return orderItem;
    }


    // -- 비지니스 로직 -- //
    // 주문 취소
    public void cancel() {
        this.getItem().addStock(count);
    }

    // -- 조회 로직 -- //
    // 총 금액
    public int getTotalPrice() {
        return getOrderPrice() * getCount();
    }
}
