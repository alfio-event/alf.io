import {Component, OnInit} from '@angular/core';
import {InfoService} from '../shared/info.service';
import {Info} from '../model/info';
import {getFromSessionStorage, writeToSessionStorage} from '../shared/util';

const hideAnnouncementBannerKey = 'alfio.hideAnnouncementBanner';

@Component({
  selector: 'app-banner-check',
  templateUrl: './banner-check.component.html',
  styleUrls: ['./banner-check.component.scss']
})
export class BannerCheckComponent implements OnInit {

  info: Info;
  secure: boolean;
  hideAlertInfo: boolean;
  hideAnnouncementBanner: boolean;

  constructor(private infoService: InfoService) { }

  ngOnInit() {
    this.hideAnnouncementBanner = !!getFromSessionStorage(hideAnnouncementBannerKey);
    this.infoService.getInfo().subscribe(info => {
      this.info = info;
      this.secure = location.protocol.indexOf('https:') === 0;
    });
  }

  dismissAnnouncementBanner(): void {
    this.hideAnnouncementBanner = true;
    writeToSessionStorage(hideAnnouncementBannerKey, 'true');
  }

}
