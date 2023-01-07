import {Directive, EventEmitter, HostListener, Input, Output} from '@angular/core';

/**
 * Based upon https://stackoverflow.com/a/52949299/9679084
 */
@Directive({ selector: '[appClipboardCopy]' })
export class ClipboardCopyDirective {

  // tslint:disable-next-line:no-input-rename
  @Input('appClipboardCopy')
  public payload: string;

  @Output()
  public copied: EventEmitter<string> = new EventEmitter<string>();

  @HostListener('click', ['$event'])
  public onClick(event: MouseEvent): void {

    event.preventDefault();
    if (!this.payload) {
      return;
    }

    const listener = (e: ClipboardEvent) => {
      const clipboard = e.clipboardData || window['clipboardData'];
      clipboard.setData('text', this.payload.toString());
      e.preventDefault();
      this.copied.emit(this.payload);
    };

    document.addEventListener('copy', listener, false);
    document.execCommand('copy');
    document.removeEventListener('copy', listener, false);
  }
}
