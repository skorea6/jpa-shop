package jpabook.jpashop.service;

import jpabook.jpashop.domain.item.Book;
import jpabook.jpashop.domain.item.Item;
import jpabook.jpashop.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ItemService {
    private final ItemRepository itemRepository;

    @Transactional
    public void saveItem(Item item) {
        itemRepository.save(item);
    }

    // 준영속 엔티티?
    // 영속성 컨텍스트가 더는 관리하지 않는 엔티티를 말한다.

    // 변경감지와 병합(merge)
    // 1. merge 병합 : 준영속 상태의 엔티티를 영속 상태로 변경할때 사용하는 기능이다. em.merge(item); => 모든 속성이 변경
    // 2. 변경 감지 기능 사용: => 원하는 속성만 변경하므로 이것을 이용하자 / Transactional 때문에 save 같은걸 호출 안해도 그냥 업데이트 됨. Transactional이 마지막에 flush 처리 해줌
    @Transactional
    public void updateItem(Long itemId, String name, int price, int stockQuantity) {
        Item findItem = itemRepository.findOne(itemId);
        // 주의!! Setter 는 아래 처럼 실무에서 쓰지 말자. findItem.change(name, price..) 와 같이 하나 만들어서 사용하는게 효율적
        findItem.setPrice(price);
        findItem.setName(name);
        findItem.setStockQuantity(stockQuantity);

        // Item 이 자동으로 바뀜. (flush 안해줘도) => 변경감지 기능!
        // 영속성 컨텍스트에서 엔티티를 다시 조회한 후에 데이터를 수정하는 방법
        // 트랜잭션 안에서 엔티티를 다시 조회, 변경할 값 선택 트랜잭션 커밋 시점에 변경 감지(Dirty Checking) 이 동작해서 데이터베이스에 UPDATE SQL 실행
    }

    public List<Item> findItems() {
        return itemRepository.findAll();
    }

    public Item findOne(Long itemId) {
        return itemRepository.findOne(itemId);
    }
}
