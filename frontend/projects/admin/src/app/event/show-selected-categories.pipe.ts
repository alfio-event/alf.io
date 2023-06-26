import {Pipe, PipeTransform} from '@angular/core';
import {TicketCategoryFilter, UiTicketCategory} from '../model/ticket-category';


@Pipe({
  name: 'showSelectedCategories',
  pure: false,
})
export class ShowSelectedCategoriesPipe implements PipeTransform {
  transform(
    categories: UiTicketCategory[],
    criteria: TicketCategoryFilter
  ): UiTicketCategory[] {
    if (criteria.active && criteria.expired && criteria.search === '') {
      return categories;
    }
    return categories.filter((category) => {
      const result =
        (criteria.active && !category.expired) ||
        (criteria.expired && category.expired);
      if (result && criteria.search !== '') {
        return (
          category.name.toLowerCase().indexOf(criteria.search.toLowerCase()) >
          -1
        );
      }
      return result;
    });
  }
}
