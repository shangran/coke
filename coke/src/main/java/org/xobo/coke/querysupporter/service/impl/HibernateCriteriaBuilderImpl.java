package org.xobo.coke.querysupporter.service.impl;

import java.util.Collection;

import javax.annotation.Resource;

import org.apache.commons.lang.StringUtils;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Service;
import org.xobo.coke.model.IBase;
import org.xobo.coke.querysupporter.service.HibernateCriteriaBuilder;
import org.xobo.coke.querysupporter.service.SynonymService;
import org.xobo.coke.service.impl.ObjectRelationQuery;

import com.bstek.dorado.data.provider.And;
import com.bstek.dorado.data.provider.Criteria;
import com.bstek.dorado.data.provider.Junction;
import com.bstek.dorado.data.provider.Or;
import com.bstek.dorado.data.provider.Order;
import com.bstek.dorado.data.provider.filter.FilterOperator;
import com.bstek.dorado.data.provider.filter.SingleValueFilterCriterion;

@Service(HibernateCriteriaBuilderImpl.BEAN_ID)
public class HibernateCriteriaBuilderImpl implements HibernateCriteriaBuilder {

	@Resource(name = SynonymService.BEAN_ID)
	private SynonymService synonymService;

	@Override
	public DetachedCriteria buildDetachedCriteria(Criteria criteria, Class<?> entityClass) {
		return buildDetachedCriteria(criteria, entityClass, null);
	}

	@Override
	public DetachedCriteria buildDetachedCriteria(Criteria criteria, Class<?> entityClass, String alias) {
		DetachedCriteria dc = null;
		if (StringUtils.isEmpty(alias)) {
			dc = DetachedCriteria.forClass(entityClass);
		} else {
			dc = DetachedCriteria.forClass(entityClass, alias);
		}
		if (criteria != null) {
			if (criteria.getCriterions().size() > 0) {
				buildCriterions(criteria.getCriterions(), dc, entityClass);
			}
			buildOrder(criteria.getOrders(), dc);
		}

		if (IBase.class.isAssignableFrom(entityClass)) {
			dc.add(Restrictions.eq("deleted", false));
		}
		return dc;
	}

	private Criterion buildCriterion(SingleValueFilterCriterion fc) {
		Criterion result = null;
		String operator = buildOperator(fc.getFilterOperator());
		String propertyName = fc.getProperty();
		Property p = Property.forName(propertyName);
		if (operator.equals("like")) {
			result = p.like("%" + fc.getValue() + "%");
		} else if (operator.startsWith("*")) {
			result = p.like((String) fc.getValue(), MatchMode.END);
		} else if (operator.endsWith("*")) {
			result = p.like((String) fc.getValue(), MatchMode.START);
		} else if (operator.equals(">")) {
			result = p.gt(fc.getValue());
		} else if (operator.equals("<")) {
			result = p.lt(fc.getValue());
		} else if (operator.equals(">=")) {
			result = p.ge(fc.getValue());
		} else if (operator.equals("<=")) {
			result = p.le(fc.getValue());
		} else if (operator.equals("=")) {
			result = p.eq(fc.getValue());
		} else if (operator.equals("<>")) {
			result = p.ne(fc.getValue());
		} else {
			throw new IllegalArgumentException("Query operator[" + operator + "] is invalide");
		}
		return result;
	}

	private void buildCriterions(Collection<com.bstek.dorado.data.provider.Criterion> criterions, DetachedCriteria dc,
			Class<?> entityClass) {
		for (com.bstek.dorado.data.provider.Criterion c : criterions) {
			if (c instanceof SingleValueFilterCriterion) {
				SingleValueFilterCriterion fc = (SingleValueFilterCriterion) c;

				Collection<String> unionProperties = synonymService.find(entityClass, fc.getProperty());
				if (unionProperties.isEmpty()) {
					dc.add(buildCriterion(fc));
				} else {
					org.hibernate.criterion.Junction junction = Restrictions.disjunction();
					Object value = "%" + fc.getValue() + "%";
					for (String property : unionProperties) {
						junction.add(Restrictions.like(property, value));
					}
					dc.add(junction);
				}

			} else if (c instanceof Junction) {
				Junction jun = (Junction) c;
				org.hibernate.criterion.Junction junction = null;
				if (jun instanceof Or) {
					junction = Restrictions.disjunction();
				} else if (jun instanceof And) {
					junction = Restrictions.conjunction();
				}
				Collection<com.bstek.dorado.data.provider.Criterion> subCriterions = jun.getCriterions();
				if (subCriterions != null) {
					buildCriterions(subCriterions, junction);
				}
				dc.add(junction);
			} else if (c instanceof ObjectRelationQuery) {
			}
		}
	}

	private void buildCriterions(Collection<com.bstek.dorado.data.provider.Criterion> criterions,
			org.hibernate.criterion.Junction dc) {
		for (com.bstek.dorado.data.provider.Criterion c : criterions) {
			if (c instanceof SingleValueFilterCriterion) {
				SingleValueFilterCriterion fc = (SingleValueFilterCriterion) c;

				dc.add(buildCriterion(fc));
			}
			if (c instanceof Junction) {
				Junction jun = (Junction) c;
				org.hibernate.criterion.Junction junction = null;
				if (jun instanceof Or) {
					junction = Restrictions.disjunction();
				} else if (jun instanceof And) {
					junction = Restrictions.conjunction();
				}
				Collection<com.bstek.dorado.data.provider.Criterion> subCriterions = jun.getCriterions();
				if (subCriterions != null) {
					buildCriterions(subCriterions, dc);
				}
				dc.add(junction);
			}
		}
	}

	private void buildOrder(Collection<Order> orders, DetachedCriteria dc) {
		if (orders != null && !orders.isEmpty()) {
			for (Order order : orders) {
				if (order.isDesc()) {
					dc.addOrder(org.hibernate.criterion.Order.desc(order.getProperty()));
				} else {
					dc.addOrder(org.hibernate.criterion.Order.asc(order.getProperty()));
				}
			}
		}
	}

	protected String buildOperator(FilterOperator filterOperator) {
		String operator = "like";
		if (filterOperator != null) {
			operator = filterOperator.toString();
		}
		return operator;
	}

}