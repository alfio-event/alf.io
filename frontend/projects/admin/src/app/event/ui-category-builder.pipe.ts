import { Pipe, PipeTransform } from '@angular/core';
import {
  TicketCategory,
  UiTicketCategory,
} from 'projects/public/src/app/model/ticket-category';

@Pipe({
  name: 'uiCategoryBuilder',
})
export class UiCategoryBuilderPipe implements PipeTransform {
  transform(categories: TicketCategory[]): UiTicketCategory[] {
    return categories.map((category) => new UiTicketCategory(category));
  }
}
