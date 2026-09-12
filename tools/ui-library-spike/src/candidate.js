import Tabs from 'primevue/tabs';
import TabList from 'primevue/tablist';
import Tab from 'primevue/tab';
import PrimeVue from 'primevue/config';
import ToastService from 'primevue/toastservice';
import Button from 'primevue/button';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Dialog from 'primevue/dialog';
import InputNumber from 'primevue/inputnumber';
import InputText from 'primevue/inputtext';
import MultiSelect from 'primevue/multiselect';
import Menu from 'primevue/menu';
import Toast from 'primevue/toast';
import FileUpload from 'primevue/fileupload';
import PickList from 'primevue/picklist';
import DateInput from 'primevue/datepicker';
import SelectInput from 'primevue/select';
import InfoOverlay from 'primevue/popover';
import Lara from '@primeuix/themes/lara';
import {definePreset} from '@primeuix/themes';
const preset = definePreset(Lara, {components: {datepicker: {date: {width: '2rem', height: '2rem'}, panel: {padding: '.5rem'}}}, semantic: {primary: {50: '#e3f2fd', 100: '#bbdefb', 200: '#90caf9', 300: '#64b5f6', 400: '#42a5f5', 500: '#007ad9', 600: '#006bc0', 700: '#005b9f', 800: '#004a80', 900: '#00395f', 950: '#00243e'}, formField: {paddingX: '.429rem', paddingY: '.429rem', borderRadius: '3px'}, colorScheme: {light: {primary: {color: '#007ad9', contrastColor: '#ffffff'}}}}});
export function configure(app) {
  app.provide('reference', false);
  Object.entries({Tabs, TabList, Tab}).forEach(([name, component]) => app.component(name, component));
  app.use(PrimeVue, {theme: {preset, options: {darkModeSelector: false}}});
  app.use(ToastService);
  Object.entries({Button, DataTable, Column, Dialog, InputNumber, InputText, MultiSelect, Menu, Toast, FileUpload, PickList, DateInput, SelectInput, InfoOverlay}).forEach(([name, component]) => app.component(name, component));
}
