import TabView from 'primevue-reference/tabview';
import TabPanel from 'primevue-reference/tabpanel';
import PrimeVue from 'primevue-reference/config';
import ToastService from 'primevue-reference/toastservice';
import Button from 'primevue-reference/button';
import DataTable from 'primevue-reference/datatable';
import Column from 'primevue-reference/column';
import Dialog from 'primevue-reference/dialog';
import InputNumber from 'primevue-reference/inputnumber';
import InputText from 'primevue-reference/inputtext';
import MultiSelect from 'primevue-reference/multiselect';
import Menu from 'primevue-reference/menu';
import Toast from 'primevue-reference/toast';
import FileUpload from 'primevue-reference/fileupload';
import PickList from 'primevue-reference/picklist';
import DateInput from 'primevue-reference/calendar';
import SelectInput from 'primevue-reference/dropdown';
import InfoOverlay from 'primevue-reference/overlaypanel';
import 'primevue-reference/resources/themes/nova/theme.css';
import 'primevue-reference/resources/primevue.min.css';
export function configure(app) {
  app.provide('reference', true);
  Object.entries({TabView, TabPanel}).forEach(([name, component]) => app.component(name, component));
  app.use(PrimeVue);
  app.use(ToastService);
  Object.entries({Button, DataTable, Column, Dialog, InputNumber, InputText, MultiSelect, Menu, Toast, FileUpload, PickList, DateInput, SelectInput, InfoOverlay}).forEach(([name, component]) => app.component(name, component));
}
