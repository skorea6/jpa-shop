package jpabook.jpashop.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

// 실무에서는 가급적 Getter는 열어두고, Setter는 꼭 필요한 경우에만 사용하는 것을 추천

@Entity // 도메인 객체로 설정 (클래스명과 같음, 수정시에는 name 속성으로) -> @Table 생략됨.
@Getter @Setter
public class Member {
    @Id @GeneratedValue // 기본키 설정
    @Column(name = "member_id") // @Entity 를 쓰면 생략 가능.
    private Long id;

//    @NotEmpty
    private String name;

    @Embedded // property 에 어노테이션 선언 / Embeddable 클래스를 Entity 에 맵핑하고 싶을 때 해당 어노테이션 사용
    private Address address;

    // 일대다 (단방향) => 아래중에 한쪽에만 설정
    // @ManyToOne
    //  - 프로퍼티가 하나일 때, 해당 어노테이션을 선언
    //  - 프로퍼티의 PK(id)가 FK(orders_id)가 됨
    // @OneToMany
    //  - 프로퍼티가 Collection 타입과 같이 여러 개를 가지는 경우 어노테이션 선언
    //  - 관계를 저장하는 테이블이 생성됨

    // 일대다 (양방향)
    // 양방향의 관계를 맺으려면 '주인 엔티티의 프로퍼티명'을 지정해줘야함. @OneToMany(mappedBy = "관계의 주인테이블의 프로퍼티명"), @OneToOne 도 마찬가지.

    // Fetch
    // @OneToMany(mappedBy = "owner", ... , fetch = FetchType.LAZY) fetch 옵션이 생략되어 있다면 기본적으로 LAZY가 적용됨
    // 반대로 @ManyToOne의 경우는 가져와야할 값이 하나밖에 없기 때문에 기본적으로 EAGER가 적용되어있음
    // 즉시로딩( EAGER )은 예측이 어렵고, 어떤 SQL이 실행될지 추적하기 어렵다. 특히 JPQL을 실행할 때 N+1 문제가 자주 발생한다.
    // 실무에서 모든 연관관계는 지연로딩( LAZY )으로 설정해야 한다.
    // @OneToOne, @ManyToOne 관계는 기본이 즉시로딩이므로 직접 지연로딩으로 설정해야 한 다.

    @OneToMany(mappedBy = "member")
    private List<Order> orders = new ArrayList<>();
}
