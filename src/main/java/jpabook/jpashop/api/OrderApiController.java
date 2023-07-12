package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderItem;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.query.OrderFlatDto;
import jpabook.jpashop.repository.order.query.OrderQueryDto;
import jpabook.jpashop.repository.order.query.OrderQueryRepository;
import jpabook.jpashop.service.query.OrderDto;
import jpabook.jpashop.service.query.OrderQueryService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * V1. 엔티티 직접 노출
 * - 엔티티가 변하면 API 스펙이 변한다.
 * - 트랜잭션 안에서 지연 로딩 필요
 * - 양방향 연관관계 문제
 * V2. 엔티티를 조회해서 DTO로 변환 (fetch join 사용X)
 * - 트랜잭션 안에서 지연 로딩 필요
 * V3. 엔티티를 조회해서 DTO로 변환 (fetch join 사용O)
 * - 페이징 시에는 N 부분을 포기해야함 (대신에 batch fetch size? 옵션 주면 N -> 1 쿼리로 변경가능)
 * V4. JPA에서 DTO로 바로 조회, 컬렉션 N 조회 (1+NQuery)
 * - 페이징 가능
 * V5. JPA에서 DTO로 바로 조회, 컬렉션 1 조회 최적화 버전 (1+1Query)
 * - 페이징 가능
 * V6. JPA에서 DTO로 바로 조회, 플랫 데이터 (1 Query) (1 Query)
 * - 페이징 불가능...
 */

/**
1. 엔티티 조회 방식으로 우선 접근 (추천)
    1. 페치조인으로 쿼리 수를 최적화 (1:N 관계가 없는 경우)
    2. 컬렉션 최적화 (1:N 관계가 있는 경우)
        1. 페이징 필요: hibernate.default_batch_fetch_size , @BatchSize 로 최적화
        2. 페이징 필요X : 페치 조인 사용
2. 엔티티 조회 방식으로 해결이 안되면 DTO 조회 방식 사용 (비추 -> 많은 코드변경 필요)
3. DTO 조회 방식으로 해결이 안되면 NativeSQL or 스프링 JdbcTemplate
 */

@RestController
@RequiredArgsConstructor
public class OrderApiController {
    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;

    /**
     * V1. 엔티티 직접 노출 (비추)
     * - Hibernate5Module 모듈 등록, LAZY=null 처리
     * - 양방향 관계 문제 발생 -> @JsonIgnore
     */
    @GetMapping("/api/v1/orders")
    public List<Order> ordersV1() {
        List<Order> all = orderRepository.findAllByString(new OrderSearch());
        for (Order order : all) {
            order.getMember().getName(); //Lazy 강제 초기화
            order.getDelivery().getAddress(); //Lazy 강제 초기화
            List<OrderItem> orderItems = order.getOrderItems();
            orderItems.stream().forEach(o -> o.getItem().getName()); //Lazy 강제 초기화
        }
        return all;
    }

    /**
     * V2. 엔티티를 조회해서 DTO로 변환 (fetch join 사용X)
     * - 지연 로딩으로 너무 많은 SQL 실행
     *      order 1번
     *      member, address N번 (order 조회 수 만큼)
     *      orderItem N번 (order 조회 수 만큼)
     *      item N번 (orderItem 조회 수 만큼)
     */
    @GetMapping("/api/v2/orders")
    public OrderResponse ordersV2() {
        List<Order> orders = orderRepository.findAllByString(new OrderSearch());
        List<OrderDto> collect = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(Collectors.toList());
        return new OrderResponse("testkey321", collect);
    }

    @Data
    @AllArgsConstructor
    static class OrderResponse {
        private String key;
        private List<OrderDto> data;
    }

    /**
     * 아래는 OSIV(Open Session In View) false 모드를 함께 적용한 상태!
     * - OSIV 전략은 트랜잭션 시작처럼 최초 데이터베이스 커넥션 시작 시점부터 API 응답이 끝날 때 까지 영속성 컨텍스트와 데이터베이스 커넥션을 유지한다.
     * - 그래서 지금까지 View Template이나 API 컨트롤러에서 지연 로딩이 가능했던 것
     * - 지연 로딩은 영속성 컨텍스트가 살아있어야 가능하고, 영속성 컨텍스트는 기본적으로 데이터베이스 커넥션을 유지한다.
     *
     * - 그런데 이 전략은 너무 오랜시간동안 데이터베이스 커넥션 리소스를 사용하기 때문에, 실시간 트래픽이 중요한 애플리케이션에서는 커넥션이 모자랄 수 있다. => 장애로 이어짐
     * - ex: 컨트롤러에서 외부 API를 호출하면 외부 API 대기 시간 만큼 커넥션 리소스를 반환하지 못하고, 유지해야 한다.
     *
     * - OSIV를 끄면 트랜잭션을 종료할 때 영속성 컨텍스트를 닫고, 데이터베이스 커넥션도 반환한다. 따라서 커넥션 리소스를 낭비하지 않는다.
     * - OSIV를 끄면 모든 지연로딩을 트랜잭션 안에서 처리해야 한다.
     * - 따라서 지금까지 작성한 많은 지연 로딩 코드를 트랜잭션 안으로 넣어야 하는 단점이 있다.
     * - 결론적으로 트랜잭션이 끝나기 전에 지연 로딩을 강제로 호출해 두어야 한다.
     *
     * - 대용량 트래픽 서버에서는 무조건 OSIV를 끄고(false) 써야하기 때문에,
     * - 핵심비지니스로직(OrderService)과 화면이나 API에 맞춘 서비스인 주로 읽기 전용 트랜잭션(OrderQueryService)로 나눠서
     * - 두 서비스 모두 트랜잭션을 유지하면서 '지연로딩'을 사용할 수 있다!
     * - spring.jpa.open-in-view: false (true가 기본값)
     */
    private final OrderQueryService orderQueryService;

    /**
     * 주문 조회 V3: 엔티티를 DTO로 변환 - 페치 조인 최적화
     * - N대일 관계만 조회할 때 사용 (중복데이터가 발생하지않고, 페이징이 필요하지 않을때)
     * - 쿼리가 딱 1번 나감. (모두 fetch join 으로 불러옴)
     */
    @GetMapping("/api/v3/orders")
    public List<OrderDto> ordersV3() {
        return orderQueryService.ordersV3();
    }

    /**
     * V3.1 엔티티를 조회해서 DTO로 변환 - 페이징과 한계 돌파
     *  1. ToOne 관계만 우선 모두 페치 조인으로 최적화 (ToOne 관계는 row수를 증가시키지 않으므로 페이징 쿼리에 영향을 주지 않음)
     *  2. 컬렉션은 지연 로딩으로 조회한다
     *  3. 지연 로딩 성능 최적화를 위해 hibernate.default_batch_fetch_size (글로벌설정) 혹은 @BatchSize (개별최적화) 를 적용한다.
     *      이 옵션을 사용하면 컬렉션이나, 프록시 객체를 한꺼번에 설정한 size 만큼 IN 쿼리로 조회한다.
     *
     * - 쿼리 호출 수가 1+N 에서 1+1로 최적화 된다.
     * - 조인보다 DB 데이터 전송량이 최적화 된다. (Order와 OrderItem을 조인하면 Order가 OrderItem 만큼 중복해서 조회된다. 이 방법은 각각 조회하므로 전송해야할 중복 데이터가 없다.)
     * - 페치 조인 방식과 비교해서 쿼리 호출 수가 약간 증가하지만, DB 데이터 전송량이 감소한다.
     * - 컬렉션 페치 조인은 페이징이 불가능 하지만 이 방법은 페이징이 가능하다.
     * - ToOne 관계는 페치 조인해도 페이징에 영향을 주지 않는다. 따라서 ToOne 관계는 페치조인으로 쿼리 수를 줄이고 해결하고, 나머지는 hibernate.default_batch_fetch_size 로 최적화 하자.
     */
    /**
     * 아래도 OSIV off 모드를 함께 적용!
     * - 1대N 관계가 하나라도 존재하거나 페이징이 필요할때 사용 (1대N 관계가 있으면 중복데이터가 발생하기 때문에.)
     * - 쿼리 총 3번 나간다. (Order fetch join 조회할때, orderItem 조회할때 in 써서, Item 조회할때 in 써서)
     * - IN(?, ? ..) 안에는 최대 1000개 hibernate.default_batch_fetch_size 로 조정.
     */
    @GetMapping("/api/v3.1/orders")
    public List<OrderDto> ordersV3_page(@RequestParam(value = "offset",defaultValue = "0") int offset,
                                        @RequestParam(value = "limit", defaultValue= "100") int limit) {
        return orderQueryService.ordersV3_page(offset, limit);
    }

    /**
     * V4: JPA에서 DTO를 직접 조회
     * - Query: 루트 1번, 컬렉션 N 번 실행
     * - ToOne(N:1, 1:1) 관계들을 먼저 1번 조회하고, ToMany(1:N) 관계는 각각 별도로 여러번 쿼리 날려서 처리한다.
     * - row 수가 증가하지 않는 ToOne 관계는 조인으로 최적화 하기 쉬우므로 한번에 조회하고, ToMany 관계는 최적화 하기 어려우므로 findOrderItems() 같은 별도의 메서드로 조회한다.
     */
    @GetMapping("/api/v4/orders")
    public List<OrderQueryDto> ordersV4() {
        List<OrderQueryDto> orders = orderQueryRepository.findOrderQueryDtos();
        return orders;
    }

    /**
     * 주문 조회 V5: JPA에서 DTO 직접 조회 - 컬렉션 조회 최적화
     * - 일대다 관계인 컬렉션은 IN 절을 활용해서 메모리에 미리 조회해서 최적화
     *
     * - Query: 루트 1번, 컬렉션 1번
     * - ToOne 관계들을 먼저 조회하고, 여기서 얻은 식별자 orderId로 ToMany 관계인 OrderItem 을 한꺼번에 조회 (IN 절 통해서)
     * - MAP을 사용해서 매칭 성능 향상 (O(1))
     */
    @GetMapping("/api/v5/orders")
    public List<OrderQueryDto> ordersV5() {
        List<OrderQueryDto> orders = orderQueryRepository.findAllByDto_optimization();
        return orders;
    }

    /**
     * 주문 조회 V6: JPA에서 DTO로 직접 조회, 플랫 데이터 최적화
     * - JOIN 결과를 그대로 조회 후 애플리케이션에서 원하는 모양으로 직접 변환
     * - 쿼리가 한방만 나가는데, 그렇게 추천하는것은 아니다.
     *
     * - Query: 1번
     * - 단점:
     *      - 쿼리는 한번이지만 조인으로 인해 DB에서 애플리케이션에 전달하는 데이터에 중복 데이터가 추가되므로 상황에 따라 V5 보다 더 느릴 수도 있다.
     *      - 애플리케이션에서 추가 작업이 크다.
     *      - 페이징 불가능
     */
    @GetMapping("/api/v6/orders")
    public List<OrderFlatDto> ordersV6() {
        List<OrderFlatDto> orders = orderQueryRepository.findAllByDto_flat();
        return orders;
    }
}
